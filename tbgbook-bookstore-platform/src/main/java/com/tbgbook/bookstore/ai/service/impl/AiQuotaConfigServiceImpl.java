package com.tbgbook.bookstore.ai.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.domain.AiQuotaConfig;
import com.tbgbook.bookstore.ai.mapper.AiQuotaConfigMapper;
import com.tbgbook.bookstore.ai.service.IAiQuotaConfigService;
import com.tbgbook.bookstore.common.exception.BizErrors;

/**
 * AI 配额配置 Service 实现。
 *
 * @author tbgbook
 */
@Service
public class AiQuotaConfigServiceImpl extends ServiceImpl<AiQuotaConfigMapper, AiQuotaConfig>
        implements IAiQuotaConfigService
{
    private static final long STORE_ID = 1L;

    @Override
    public List<AiQuotaConfig> selectAiQuotaConfigList(AiQuotaConfig query)
    {
        LambdaQueryWrapper<AiQuotaConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiQuotaConfig::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.eq(query.getScope() != null, AiQuotaConfig::getScope, query.getScope());
            wrapper.eq(StringUtils.hasText(query.getScopeValue()),
                    AiQuotaConfig::getScopeValue, query.getScopeValue());
            wrapper.eq(StringUtils.hasText(query.getStatus()), AiQuotaConfig::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(AiQuotaConfig::getConfigId);
        return list(wrapper);
    }

    @Override
    public int addQuotaConfig(AiQuotaConfig config)
    {
        validate(config);
        config.setConfigId(null);
        config.setStoreId(STORE_ID);
        config.setScopeValue(config.getScopeValue() == null ? "" : config.getScopeValue());
        config.setHourlyLimit(config.getHourlyLimit() == null ? 20 : config.getHourlyLimit());
        config.setDailyLimit(config.getDailyLimit() == null ? 100 : config.getDailyLimit());
        config.setMonthBudget(config.getMonthBudget() == null ? BigDecimal.ZERO : config.getMonthBudget());
        config.setGlobalDailyLimit(config.getGlobalDailyLimit() == null ? 10000 : config.getGlobalDailyLimit());
        config.setOverLimitStrategy(config.getOverLimitStrategy() == null ? "" : config.getOverLimitStrategy());
        config.setStatus(StringUtils.hasText(config.getStatus()) ? config.getStatus() : "0");
        return save(config) ? 1 : 0;
    }

    @Override
    public int updateQuotaConfig(AiQuotaConfig config)
    {
        if (config == null || config.getConfigId() == null)
        {
            throw BizErrors.badRequest("配置ID不能为空");
        }
        if (getById(config.getConfigId()) == null)
        {
            throw BizErrors.notFound("配额配置", config.getConfigId());
        }
        validate(config);
        config.setStoreId(null);
        return updateById(config) ? 1 : 0;
    }

    @Override
    public int removeQuotaConfigs(Long[] configIds)
    {
        if (configIds == null || configIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的配置");
        }
        for (Long configId : configIds)
        {
            if (getById(configId) == null)
            {
                throw BizErrors.notFound("配额配置", configId);
            }
        }
        return baseMapper.deleteBatchIds(List.of(configIds));
    }

    private void validate(AiQuotaConfig config)
    {
        if (config == null)
        {
            throw BizErrors.badRequest("配置不能为空");
        }
        if (config.getScope() != null && (config.getScope() < 1 || config.getScope() > 3))
        {
            throw BizErrors.badRequest("范围取值 1全局 / 2单会员 / 3按场景");
        }
        if (config.getHourlyLimit() != null && config.getHourlyLimit() <= 0)
        {
            throw BizErrors.badRequest("小时限流必须大于 0");
        }
        if (config.getDailyLimit() != null && config.getDailyLimit() <= 0)
        {
            throw BizErrors.badRequest("日限流必须大于 0");
        }
        if (config.getStatus() != null && !"0".equals(config.getStatus()) && !"1".equals(config.getStatus()))
        {
            throw BizErrors.badRequest("状态取值 0正常 / 1停用");
        }
    }
}
