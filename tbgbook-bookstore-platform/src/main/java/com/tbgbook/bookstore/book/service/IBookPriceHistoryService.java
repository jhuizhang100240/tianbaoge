package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.BookPriceHistory;

/**
 * 图书价格版本历史 Service 接口（**只读**）。
 *
 * <p>写入由 {@code BookServiceImpl} 在图书改价时完成（FR-4.1 规则 5），
 * 本服务只提供查询 —— 不暴露任何写方法，避免绕过改价流程手工插历史。
 *
 * @author tbgbook
 */
public interface IBookPriceHistoryService extends IService<BookPriceHistory>
{
    /**
     * 查询价格版本历史列表。
     *
     * @param history 查询条件
     * @return 历史记录集合
     */
    List<BookPriceHistory> selectBookPriceHistoryList(BookPriceHistory history);
}
