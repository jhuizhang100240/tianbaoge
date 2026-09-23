package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.BookImportRow;

/**
 * 图书批量导入明细行 Service 接口。
 *
 * <p>明细行由解析阶段生成，**不由接口写入**。CRUD 端点保留是因为接口文档的
 * 「图书批量导入明细行」资源声明了 5 个端点，但写路径实际只有解析与执行两处。
 *
 * @author tbgbook
 */
public interface IBookImportRowService extends IService<BookImportRow>
{
    /**
     * 查询明细行列表。
     *
     * @param row 查询条件（batchId / rowStatus / isbn）
     * @return 明细行集合（不含 rawData，列表用不上整行快照）
     */
    List<BookImportRow> selectImportRowList(BookImportRow row);
}
