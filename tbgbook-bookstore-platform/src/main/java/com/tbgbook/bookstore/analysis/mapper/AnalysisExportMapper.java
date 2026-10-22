package com.tbgbook.bookstore.analysis.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * 导出数据查询（复杂 SQL 走 XML）。
 */
public interface AnalysisExportMapper
{
    List<Map<String, Object>> exportOrders(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> exportInventory(@Param("categoryId") Long categoryId);

    List<Map<String, Object>> exportMembers();

    List<Map<String, Object>> exportMemberData(@Param("memberId") Long memberId);

    List<Map<String, Object>> exportBorrows(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Map<String, Object>> exportStockcheckDiff(@Param("start") LocalDate start,
            @Param("end") LocalDate end);

    List<Map<String, Object>> customReportRows(@Param("start") LocalDate start, @Param("end") LocalDate end,
            @Param("dimension") String dimension, @Param("limit") int limit);
}
