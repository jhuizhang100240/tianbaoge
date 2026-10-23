package com.tbgbook.bookstore.book.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.BookChangeLog;
import com.tbgbook.bookstore.book.mapper.BookChangeLogMapper;
import com.tbgbook.bookstore.book.service.IBookChangeLogService;

/**
 * 图书信息变更日志 Service 业务层处理（只读）。
 *
 * <p>故意**不提供**写方法：变更日志是审计证据（FR-4.1.9），
 * 写入路径唯一 —— {@code BookServiceImpl#writeChangeLogs}。
 *
 * @author tbgbook
 */
@Service
public class BookChangeLogServiceImpl
        extends ServiceImpl<BookChangeLogMapper, BookChangeLog>
        implements IBookChangeLogService
{
    @Override
    public List<BookChangeLog> selectBookChangeLogList(BookChangeLog log)
    {
        return baseMapper.selectBookChangeLogList(log);
    }
}
