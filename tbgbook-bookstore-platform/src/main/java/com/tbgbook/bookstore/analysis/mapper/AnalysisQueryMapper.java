package com.tbgbook.bookstore.analysis.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * 数据分析聚合查询（复杂 SQL 走 XML，Service 不出现 SQL 字符串）。
 */
public interface AnalysisQueryMapper
{
    Map<String, Object> dayOrderMetrics(@Param("day") LocalDate day);

    Map<String, Object> dayProfitMetrics(@Param("day") LocalDate day);

    Map<String, Object> saleSummary(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> saleByDimension(@Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("dimension") String dimension);

    List<Map<String, Object>> bookRankSale(@Param("start") LocalDate start, @Param("end") LocalDate end,
            @Param("limit") int limit, @Param("orderByProfit") boolean orderByProfit);

    List<Map<String, Object>> bookRankSlow(@Param("threshold") java.time.LocalDateTime threshold,
            @Param("limit") int limit);

    List<Map<String, Object>> categoryShareRange(@Param("start") LocalDate start,
            @Param("end") LocalDate end);

    List<Map<String, Object>> memberNewTrend(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> memberLevelDistribution();

    Map<String, Object> memberActiveStats(@Param("start") LocalDate start, @Param("end") LocalDate end);

    Map<String, Object> memberRepurchase(@Param("start") LocalDate start, @Param("end") LocalDate end);

    Map<String, Object> memberConsumeRatio(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> memberRetentionWeekly(@Param("start") LocalDate start,
            @Param("end") LocalDate end);

    List<Map<String, Object>> memberRetentionMonthly(@Param("start") LocalDate start,
            @Param("end") LocalDate end);

    Map<String, Object> inventoryStructure(@Param("categoryId") Long categoryId);

    Map<String, Object> inventorySales(@Param("start") LocalDate start, @Param("end") LocalDate end,
            @Param("categoryId") Long categoryId);

    Map<String, Object> inventoryStockKinds(@Param("categoryId") Long categoryId);

    List<Map<String, Object>> outOfStock(@Param("categoryId") Long categoryId);

    Map<String, Object> inventoryTotalAmount(@Param("categoryId") Long categoryId);

    Map<String, Object> textbookSeasonHeader(@Param("listId") Long listId, @Param("term") String term);

    List<Map<String, Object>> textbookSeasonItems(@Param("listId") Long listId, @Param("term") String term);

    List<Map<String, Object>> rangeOrderMetrics(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> rangeProfitMetrics(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> metricRows(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 已有完整 8 个指标的日期（避免按订单表判断导致空订单日反复重算）。 */
    List<LocalDate> metricCompleteDates(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> topBooks(@Param("day") LocalDate day, @Param("limit") int limit);

    List<Map<String, Object>> categoryShare(@Param("day") LocalDate day);

    Map<String, Object> screenMemberBlock(@Param("day") LocalDate day);
}
