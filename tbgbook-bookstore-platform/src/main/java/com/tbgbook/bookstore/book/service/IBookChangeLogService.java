package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.BookChangeLog;

/**
 * 图书信息变更日志 Service 接口（**只读**）。
 *
 * <p>写入由 {@code BookServiceImpl} 在图书编辑时完成（FR-4.1.9），
 * 本服务只提供查询 —— 变更日志是审计证据，不允许经接口篡改。
 *
 * @author tbgbook
 */
public interface IBookChangeLogService extends IService<BookChangeLog>
{
    /**
     * 查询图书信息变更日志列表。
     *
     * @param log 查询条件
     * @return 日志集合
     */
    List<BookChangeLog> selectBookChangeLogList(BookChangeLog log);
}
