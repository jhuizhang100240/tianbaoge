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

    List<Map<String, Object>> rangeOrderMetrics(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> rangeProfitMetrics(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> metricRows(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> topBooks(@Param("day") LocalDate day, @Param("limit") int limit);

    List<Map<String, Object>> categoryShare(@Param("day") LocalDate day);

    Map<String, Object> screenMemberBlock(@Param("day") LocalDate day);
}
