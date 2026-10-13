package com.tbgbook.bookstore.ai.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.AiCallLog;

/**
 * AI 调用明细 Service（只读 + 成本聚合）。
 *
 * @author tbgbook
 */
public interface IAiCallLogService extends IService<AiCallLog>
{
    List<AiCallLog> selectAiCallLogList(AiCallLog query);

    /**
     * 成本监控（A-76）：summary / trend / byScene / byModel。
     */
    Map<String, Object> costSummary(LocalDate start, LocalDate end, String granularity, Integer scene);
}
