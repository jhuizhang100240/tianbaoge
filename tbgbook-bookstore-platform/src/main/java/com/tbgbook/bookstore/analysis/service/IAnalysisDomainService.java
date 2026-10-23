package com.tbgbook.bookstore.analysis.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 会员 / 库存 / 教材季分析 Service（FR-4.6.5/4.6.6/4.6.8）。
 */
public interface IAnalysisDomainService
{
    Map<String, Object> memberSummary(LocalDate startDate, LocalDate endDate);

    Map<String, Object> inventorySummary(LocalDate startDate, LocalDate endDate, Long categoryId);

    Map<String, Object> textbookSeason(Long listId, String term);
}
