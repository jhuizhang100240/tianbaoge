package com.tbgbook.bookstore.analysis.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.analysis.domain.BizTodo;
import com.tbgbook.bookstore.analysis.domain.ReportPushLog;
import com.tbgbook.bookstore.analysis.domain.ReportSubscribe;
import com.tbgbook.bookstore.analysis.mapper.ReportPushLogMapper;
import com.tbgbook.bookstore.analysis.mapper.ReportSubscribeMapper;
import com.tbgbook.bookstore.analysis.service.IReportSubscribeService;
import com.tbgbook.bookstore.analysis.service.ITodoService;
import com.tbgbook.bookstore.common.exception.BizErrors;

import lombok.RequiredArgsConstructor;

/**
 * 报表订阅 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class ReportSubscribeServiceImpl extends ServiceImpl<ReportSubscribeMapper, ReportSubscribe>
        implements IReportSubscribeService
{
    private static final long STORE_ID = 1L;

    private static final Set<String> REPORT_CODES = Set.of(
            "sale_summary", "member_summary", "inventory_summary", "textbook_season", "dashboard_overview");

    private static final Pattern PUSH_TIME = Pattern.compile("^(每日\\d{2}:\\d{2}|每周[1-7]\\s+\\d{2}:\\d{2})$");

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final ReportPushLogMapper pushLogMapper;

    private final ITodoService todoService;

    @Override
    public List<ReportSubscribe> selectSubscribeList(ReportSubscribe query)
    {
        LambdaQueryWrapper<ReportSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportSubscribe::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.like(StringUtils.hasText(query.getSubscribeName()),
                    ReportSubscribe::getSubscribeName, query.getSubscribeName());
            wrapper.eq(query.getReportType() != null, ReportSubscribe::getReportType, query.getReportType());
            wrapper.eq(query.getStatus() != null, ReportSubscribe::getStatus, query.getStatus());
            wrapper.eq(query.getPushChannel() != null, ReportSubscribe::getPushChannel, query.getPushChannel());
        }
        wrapper.orderByDesc(ReportSubscribe::getSubscribeId);
        return list(wrapper);
    }

    @Override
    public ReportSubscribe getSubscribe(Long subscribeId)
    {
        ReportSubscribe subscribe = getById(subscribeId);
        if (subscribe == null)
        {
            throw BizErrors.notFound("订阅配置", subscribeId);
        }
        return subscribe;
    }

    @Override
    public int addSubscribe(ReportSubscribe subscribe)
    {
        if (subscribe == null)
        {
            throw BizErrors.badRequest("订阅配置不能为空");
        }
        subscribe.setSubscribeId(null);
        subscribe.setStoreId(STORE_ID);
        subscribe.setStatus(subscribe.getStatus() == null ? 1 : subscribe.getStatus());
        subscribe.setFailReason("");
        subscribe.setDelFlag(null);
        validate(subscribe);
        requireUniqueName(subscribe.getSubscribeName(), null);
        return save(subscribe) ? 1 : 0;
    }

    @Override
    public int updateSubscribe(ReportSubscribe subscribe)
    {
        if (subscribe == null || subscribe.getSubscribeId() == null)
        {
            throw BizErrors.badRequest("订阅ID不能为空");
        }
        ReportSubscribe existing = getSubscribe(subscribe.getSubscribeId());
        ReportSubscribe merged = merge(existing, subscribe);
        validate(merged);
        requireUniqueName(merged.getSubscribeName(), subscribe.getSubscribeId());

        subscribe.setStoreId(null);
        subscribe.setFailReason(null);
        subscribe.setDelFlag(null);
        subscribe.setCreateBy(null);
        subscribe.setCreateTime(null);
        return updateById(subscribe) ? 1 : 0;
    }

    @Override
    public int removeSubscribes(Long[] subscribeIds)
    {
        if (subscribeIds == null || subscribeIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的订阅配置");
        }
        for (Long id : subscribeIds)
        {
            getSubscribe(id);
        }
        removeByIds(List.of(subscribeIds));
        return subscribeIds.length;
    }

    @Override
    public Map<String, Object> testPush(Long subscribeId)
    {
        ReportSubscribe subscribe = getSubscribe(subscribeId);
        validate(subscribe);
        LocalDateTime now = LocalDateTime.now();
        int channel = subscribe.getPushChannel();
        boolean includeMessage = channel == 1 || channel == 3;
        boolean includeEmail = channel == 2 || channel == 3;
        int status = includeMessage ? 1 : 2;
        int targetCount = includeMessage ? 1 : 0;
        String failReason = includeEmail ? "邮件通道未配置（阶段十启用）" : "";

        if (includeMessage)
        {
            BizTodo todo = new BizTodo();
            todo.setStoreId(STORE_ID);
            todo.setTodoType(5);
            todo.setPriority(2);
            todo.setSourceModule("analysis");
            todo.setSourceNo("SUB" + subscribeId);
            todo.setTargetValue(subscribe.getSubscribeName());
            todo.setContent("报表试推送：" + subscribe.getSubscribeName());
            todo.setLinkUrl("/analysis/subscribe");
            todo.setStatus(1);
            todo.setCreateTime(now);
            todoService.save(todo);
        }
        ReportPushLog log = new ReportPushLog();
        log.setSubscribeId(subscribeId);
        log.setPushTime(now);
        log.setPushChannel(channel);
        log.setTargetCount(targetCount);
        log.setIsTest(1);
        log.setStatus(status);
        log.setFailReason(failReason);
        log.setCreateTime(now);
        pushLogMapper.insert(log);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pushLogId", log.getLogId());
        data.put("status", status);
        data.put("targetCount", targetCount);
        data.put("failReason", failReason);
        return data;
    }

    @Override
    public List<ReportPushLog> selectPushLogList(ReportPushLog query)
    {
        LambdaQueryWrapper<ReportPushLog> wrapper = new LambdaQueryWrapper<>();
        if (query != null)
        {
            wrapper.eq(query.getSubscribeId() != null, ReportPushLog::getSubscribeId, query.getSubscribeId());
            wrapper.eq(query.getStatus() != null, ReportPushLog::getStatus, query.getStatus());
            wrapper.eq(query.getIsTest() != null, ReportPushLog::getIsTest, query.getIsTest());
        }
        wrapper.orderByDesc(ReportPushLog::getLogId);
        return pushLogMapper.selectList(wrapper);
    }

    @Override
    public ReportPushLog getPushLog(Long logId)
    {
        ReportPushLog log = pushLogMapper.selectById(logId);
        if (log == null)
        {
            throw BizErrors.notFound("推送记录", logId);
        }
        return log;
    }

    private void validate(ReportSubscribe subscribe)
    {
        if (!StringUtils.hasText(subscribe.getSubscribeName()))
        {
            throw BizErrors.badRequest("订阅名称不能为空");
        }
        if (subscribe.getReportType() == null || subscribe.getReportType() < 1 || subscribe.getReportType() > 3)
        {
            throw BizErrors.badRequest("报表类型取值 1日报/2周报/3月报");
        }
        if (!REPORT_CODES.contains(subscribe.getReportCode()))
        {
            throw BizErrors.ruleViolated("报表编码无效");
        }
        if (subscribe.getPushChannel() == null || subscribe.getPushChannel() < 1
                || subscribe.getPushChannel() > 3)
        {
            throw BizErrors.badRequest("推送方式取值 1站内信/2邮件/3站内信+邮件");
        }
        if (subscribe.getStatus() != null && subscribe.getStatus() != 1 && subscribe.getStatus() != 2)
        {
            throw BizErrors.ruleViolated("状态取值 1启用/2停用");
        }
        if (Integer.valueOf(1).equals(subscribe.getStatus()) && !StringUtils.hasText(subscribe.getPushTime()))
        {
            throw BizErrors.ruleViolated("启用订阅必须配置推送时间（如 每日08:30）");
        }
        if (StringUtils.hasText(subscribe.getPushTime())
                && !PUSH_TIME.matcher(subscribe.getPushTime().trim()).matches())
        {
            throw BizErrors.ruleViolated("推送时间格式必须为 每日HH:mm 或 每周N HH:mm");
        }
        boolean emailChannel = subscribe.getPushChannel() == 2 || subscribe.getPushChannel() == 3;
        if (emailChannel && !StringUtils.hasText(subscribe.getReceiveEmails()))
        {
            throw BizErrors.ruleViolated("邮件渠道必须配置接收邮箱");
        }
        if (StringUtils.hasText(subscribe.getReceiveEmails()))
        {
            for (String email : subscribe.getReceiveEmails().split(","))
            {
                if (!EMAIL.matcher(email.trim()).matches())
                {
                    throw BizErrors.ruleViolated("邮箱格式非法：" + email.trim());
                }
            }
        }
        if (StringUtils.hasText(subscribe.getTargetRoles()))
        {
            for (String role : subscribe.getTargetRoles().split(","))
            {
                try
                {
                    Long.parseLong(role.trim());
                }
                catch (NumberFormatException e)
                {
                    throw BizErrors.ruleViolated("接收角色必须为角色ID列表");
                }
            }
        }
    }

    private void requireUniqueName(String name, Long excludeId)
    {
        LambdaQueryWrapper<ReportSubscribe> wrapper = new LambdaQueryWrapper<ReportSubscribe>()
                .eq(ReportSubscribe::getStoreId, STORE_ID)
                .eq(ReportSubscribe::getSubscribeName, name);
        if (excludeId != null)
        {
            wrapper.ne(ReportSubscribe::getSubscribeId, excludeId);
        }
        if (count(wrapper) > 0)
        {
            throw BizErrors.conflict("订阅名称已存在：" + name);
        }
    }

    private ReportSubscribe merge(ReportSubscribe existing, ReportSubscribe update)
    {
        ReportSubscribe merged = new ReportSubscribe();
        merged.setSubscribeName(StringUtils.hasText(update.getSubscribeName())
                ? update.getSubscribeName() : existing.getSubscribeName());
        merged.setReportType(update.getReportType() == null ? existing.getReportType() : update.getReportType());
        merged.setReportCode(StringUtils.hasText(update.getReportCode())
                ? update.getReportCode() : existing.getReportCode());
        merged.setDataScope(update.getDataScope() == null ? existing.getDataScope() : update.getDataScope());
        merged.setTargetRoles(update.getTargetRoles() == null ? existing.getTargetRoles() : update.getTargetRoles());
        merged.setReceiveEmails(update.getReceiveEmails() == null
                ? existing.getReceiveEmails() : update.getReceiveEmails());
        merged.setPushTime(update.getPushTime() == null ? existing.getPushTime() : update.getPushTime());
        merged.setPushChannel(update.getPushChannel() == null ? existing.getPushChannel() : update.getPushChannel());
        merged.setStatus(update.getStatus() == null ? existing.getStatus() : update.getStatus());
        return merged;
    }
}
