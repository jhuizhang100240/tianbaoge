package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.BookImportBatch;

/**
 * 图书批量导入批次 Mapper。
 *
 * @author tbgbook
 */
public interface BookImportBatchMapper extends BaseMapper<BookImportBatch>
{
    /**
     * 查询导入批次列表。
     *
     * @param batch 查询条件（batchNo 精确、status 精确、创建时间区间）
     * @return 批次集合
     */
    List<BookImportBatch> selectImportBatchList(BookImportBatch batch);
}
