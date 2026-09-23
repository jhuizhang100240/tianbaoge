package com.tbgbook.bookstore.book.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.BookPriceHistory;
import com.tbgbook.bookstore.book.mapper.BookPriceHistoryMapper;
import com.tbgbook.bookstore.book.service.IBookPriceHistoryService;

/**
 * 图书价格版本历史 Service 业务层处理（只读）。
 *
 * <p>故意**不提供**新增/修改/删除方法：本表只增不改不删（FR-4.1 规则 5），
 * 写入路径唯一 —— {@code BookServiceImpl#writePriceHistory}。
 * 接口层没有写入口，审计链就不会被绕过。
 *
 * @author tbgbook
 */
@Service
public class BookPriceHistoryServiceImpl
        extends ServiceImpl<BookPriceHistoryMapper, BookPriceHistory>
        implements IBookPriceHistoryService
{
    @Override
    public List<BookPriceHistory> selectBookPriceHistoryList(BookPriceHistory history)
    {
        return baseMapper.selectBookPriceHistoryList(history);
    }
}
