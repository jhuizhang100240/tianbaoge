package com.tbgbook.bookstore.ai.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.AiSummary;

/**
 * 图书摘要与书评 Service。
 *
 * @author tbgbook
 */
public interface IAiSummaryService extends IService<AiSummary>
{
    List<AiSummary> selectAiSummaryList(AiSummary query);

    int addAiSummary(AiSummary summary);

    int updateAiSummary(AiSummary summary);

    int removeAiSummaries(Long[] summaryIds);

    /**
     * 生成（单本或批量；结果一律 status=1 待审核；AI 不可用 503 且不落行）。
     */
    Map<String, Object> generate(Long[] bookIds, Integer genType, String style);

    /**
     * 审核：通过 → status=4 已发布并同步 biz_book.summary/summary_source=2；驳回必填原因。
     */
    Map<String, Object> audit(Long summaryId, Integer auditResult, String rejectReason, String content);
}
