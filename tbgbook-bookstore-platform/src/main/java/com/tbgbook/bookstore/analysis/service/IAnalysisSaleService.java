package com.tbgbook.bookstore.analysis.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 销售分析与商品排行 Service（FR-4.6.3/4.6.4）。
 */
public interface IAnalysisSaleService
{
    Map<String, Object> saleSummary(LocalDate startDate, LocalDate endDate, String dimension);

    Map<String, Object> saleTrend(String granularity);

    Map<String, Object> bookRank(String rankType, LocalDate startDate, LocalDate endDate, Integer limit);
}
