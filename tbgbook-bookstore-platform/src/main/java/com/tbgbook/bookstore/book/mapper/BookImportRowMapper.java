package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.BookImportRow;

/**
 * 图书批量导入明细行 Mapper。
 *
 * @author tbgbook
 */
public interface BookImportRowMapper extends BaseMapper<BookImportRow>
{
    /**
     * 查询明细行列表。
     *
     * @param row 查询条件（batchId 精确、rowStatus 精确、isbn 精确）
     * @return 明细行集合
     */
    List<BookImportRow> selectImportRowList(BookImportRow row);

    /**
     * 查询某批次下**可参与导入**的行（1可导入 + 2警告），按 Excel 行号升序。
     *
     * <p>执行阶段用。不在这里过滤 {@code skipWarnRows} —— 是否跳过警告行是执行参数，
     * 放在 SQL 里会让「警告行有几条」这件事变得不可见；在 Service 里过滤才能同时
     * 把它们记进失败明细。
     *
     * @param batchId 批次ID
     * @return 明细行集合（含 rawData）
     */
    List<BookImportRow> selectImportableRows(@Param("batchId") Long batchId);

    /**
     * 统计某批次下各状态的行数，用于执行完成后回写汇总。
     *
     * @param batchId 批次ID
     * @param rowStatus 行状态
     * @return 行数
     */
    long countByStatus(@Param("batchId") Long batchId, @Param("rowStatus") Integer rowStatus);
}
