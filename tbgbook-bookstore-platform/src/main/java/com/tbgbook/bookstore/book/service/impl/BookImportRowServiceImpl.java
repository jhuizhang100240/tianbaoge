package com.tbgbook.bookstore.book.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.BookImportRow;
import com.tbgbook.bookstore.book.mapper.BookImportRowMapper;
import com.tbgbook.bookstore.book.service.IBookImportRowService;

/**
 * 图书批量导入明细行 Service 实现。
 *
 * <p>纯读：写路径在 {@code BookImportBatchServiceImpl}（解析时批量插入、执行时改行状态）。
 *
 * @author tbgbook
 */
@Service
public class BookImportRowServiceImpl extends ServiceImpl<BookImportRowMapper, BookImportRow>
        implements IBookImportRowService
{
    @Override
    public List<BookImportRow> selectImportRowList(BookImportRow row)
    {
        return baseMapper.selectImportRowList(row);
    }
}
