package com.tbgbook.bookstore.job.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Param;

/**
 * 流水归档 Mapper：按批复制、校验后删除。
 *
 * <p>三张源表结构不同，拆成独立语句而不是拼表名 —— 表名不能参数化，
 * 拼字符串会违反 SQL 参数化约束（NFR-S6）。
 */
public interface FlowArchiveMapper
{
    /** 复制超期库存流水到归档表，返回本次复制行数 */
    int copyStockFlows(@Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit, @Param("archivedAt") LocalDateTime archivedAt);

    /** 删除「已复制」的库存流水，返回删除行数 */
    int deleteArchivedStockFlows(@Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    /** 复制超期积分流水 */
    int copyPointLogs(@Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit, @Param("archivedAt") LocalDateTime archivedAt);

    /** 删除已归档积分流水 */
    int deleteArchivedPointLogs(@Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    /** 复制超期 AI 调用明细 */
    int copyAiCallLogs(@Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit, @Param("archivedAt") LocalDateTime archivedAt);

    /** 删除已归档 AI 调用明细 */
    int deleteArchivedAiCallLogs(@Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
}
