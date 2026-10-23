package com.tbgbook.bookstore.ai.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RBucket;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.ai.domain.AiCallLog;
import com.tbgbook.bookstore.ai.domain.AiQuotaConfig;
import com.tbgbook.bookstore.ai.mapper.AiCallLogMapper;
import com.tbgbook.bookstore.ai.mapper.AiQuotaConfigMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 配额与限流守卫（Redisson RRateLimiter）。
 *
 * <p>会员端按小时/日双窗口限流（429）；后台生成类按全局日配额（429）与月度预算（503）
 * 控制，见 spec §5.6。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class QuotaGuard
{
    private static final long STORE_ID = 1L;

    private static final int DEFAULT_HOURLY = 20;

    private static final int DEFAULT_DAILY = 100;

    private static final int DEFAULT_GLOBAL_DAILY = 10000;

    private final RedissonClient redissonClient;

    private final ISysConfigService configService;

    private final AiQuotaConfigMapper quotaConfigMapper;

    private final AiCallLogMapper aiCallLogMapper;

    public void checkMember(long memberId)
    {
        checkMember(memberId, null);
    }

    /**
     * 会员限流：按契约优先级解析 单会员(scope=2) &gt; 场景(scope=3) &gt; 全局(scope=1)
     * &gt; {@code sys_config} 默认值；停用行不参与。
     */
    public void checkMember(long memberId, String scene)
    {
        AiQuotaConfig memberConfig = resolve(2, String.valueOf(memberId));
        AiQuotaConfig sceneConfig = scene == null ? null : resolve(3, scene);
        AiQuotaConfig global = globalConfig();
        int hourly = firstPositive(intConfig("biz.ai.rate.hourly", DEFAULT_HOURLY),
                memberConfig == null ? null : memberConfig.getHourlyLimit(),
                sceneConfig == null ? null : sceneConfig.getHourlyLimit(),
                global == null ? null : global.getHourlyLimit());
        int daily = firstPositive(intConfig("biz.ai.rate.daily", DEFAULT_DAILY),
                memberConfig == null ? null : memberConfig.getDailyLimit(),
                sceneConfig == null ? null : sceneConfig.getDailyLimit(),
                global == null ? null : global.getDailyLimit());
        acquire(AiRedisKeys.rate("member-hour", String.valueOf(memberId), "hour"),
                hourly, RateIntervalUnit.HOURS, "已达本小时 AI 使用上限");
        acquire(AiRedisKeys.rate("member-day", String.valueOf(memberId), "day"),
                daily, RateIntervalUnit.DAYS, "已达当日 AI 使用上限");
    }

    public void checkGlobalDaily()
    {
        AiQuotaConfig global = globalConfig();
        int limit = global != null && global.getGlobalDailyLimit() != null
                ? global.getGlobalDailyLimit() : DEFAULT_GLOBAL_DAILY;
        acquire(AiRedisKeys.rate("global-day", "all", "day"), limit, RateIntervalUnit.DAYS,
                "今日 AI 调用总量已达上限，请明日再试");
    }

    public void checkMonthBudget()
    {
        AiQuotaConfig global = globalConfig();
        BigDecimal budget = global == null ? null : global.getMonthBudget();
        if (budget == null || budget.signum() <= 0)
        {
            return;
        }
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        List<AiCallLog> logs = aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .select(AiCallLog::getCost)
                .ge(AiCallLog::getCreateTime, monthStart)
                .lt(AiCallLog::getCreateTime, monthEnd));
        BigDecimal spent = BigDecimal.ZERO;
        for (AiCallLog item : logs)
        {
            if (item.getCost() != null)
            {
                spent = spent.add(item.getCost());
            }
        }
        if (spent.compareTo(budget) >= 0)
        {
            throw BizErrors.serviceUnavailable("已超出本月 AI 预算，请联系管理员调整");
        }
    }

    /**
     * 按场景取轻量模型（biz_ai_quota_config.scene_model_map；缺省返回 null = 用 starter 默认模型）。
     */
    public String sceneModel(String sceneKey)
    {
        String model = modelOf(resolve(3, sceneKey), sceneKey);
        if (model != null)
        {
            return model;
        }
        return modelOf(globalConfig(), sceneKey);
    }

    private void acquire(String key, int limit, RateIntervalUnit unit, String message)
    {
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        // 限额可运营调整：与上次生效值不同则重建限流器（当前窗口重新计数），否则沿用
        RBucket<Integer> limitBucket = redissonClient.getBucket(key + ":limit");
        Integer currentLimit = limitBucket.get();
        if (currentLimit == null || currentLimit != limit)
        {
            limiter.delete();
            limiter.trySetRate(RateType.OVERALL, limit, 1, unit);
            limitBucket.set(limit);
        }
        else
        {
            limiter.trySetRate(RateType.OVERALL, limit, 1, unit);
        }
        if (!limiter.tryAcquire())
        {
            throw BizErrors.tooManyRequests(message);
        }
    }

    private AiQuotaConfig globalConfig()
    {
        return resolve(1, "");
    }

    /**
     * 按范围取生效配置（status='0' 正常；停用行不参与）。
     */
    private AiQuotaConfig resolve(int scope, String scopeValue)
    {
        return quotaConfigMapper.selectOne(new LambdaQueryWrapper<AiQuotaConfig>()
                .eq(AiQuotaConfig::getStoreId, STORE_ID)
                .eq(AiQuotaConfig::getScope, scope)
                .eq(AiQuotaConfig::getScopeValue, scopeValue)
                .eq(AiQuotaConfig::getStatus, "0")
                .last("limit 1"));
    }

    private String modelOf(AiQuotaConfig config, String sceneKey)
    {
        if (config == null || config.getSceneModelMap() == null)
        {
            return null;
        }
        String model = config.getSceneModelMap().get(sceneKey);
        return StringUtils.hasText(model) ? model : null;
    }

    private int firstPositive(int fallback, Integer... values)
    {
        for (Integer value : values)
        {
            if (value != null && value > 0)
            {
                return value;
            }
        }
        return fallback;
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (!StringUtils.hasText(raw))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }
}
