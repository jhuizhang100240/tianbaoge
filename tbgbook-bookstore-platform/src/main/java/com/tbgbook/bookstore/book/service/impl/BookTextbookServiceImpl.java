package com.tbgbook.bookstore.book.service.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.BookTextbook;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.book.mapper.BookTextbookMapper;
import com.tbgbook.bookstore.book.service.IBookTextbookService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;

import lombok.RequiredArgsConstructor;

/**
 * 教材扩展信息 Service 业务层处理。
 *
 * <p>演示**1:1 扩展表**的写法（主键即外键），与主档的差异有三点：
 * <ol>
 *   <li>主键由客户端提供（{@code IdType.INPUT}），不是自增 —— 因此「新增」前必须
 *       自己判重，否则会撞主键报数据库错（而不是可读的 409）</li>
 *   <li>只有**教材类**图书（{@code book_type=2}）才能建档（FR-4.1 规则 6「非教材无行」）</li>
 *   <li>本表无 {@code del_flag}，删除是**物理删除** —— 扩展属性没有保留价值，
 *       而历史追溯由图书变更日志承担</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class BookTextbookServiceImpl extends ServiceImpl<BookTextbookMapper, BookTextbook>
        implements IBookTextbookService
{
    /** 图书类型：2 = 教材（需求 §4.1 规则 6） */
    private static final int BOOK_TYPE_TEXTBOOK = 2;

    private final ReferenceGuard referenceGuard;
    private final BookMapper bookMapper;

    @Override
    public List<BookTextbook> selectBookTextbookList(BookTextbook textbook)
    {
        return baseMapper.selectBookTextbookList(textbook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addBookTextbook(BookTextbook textbook)
    {
        Long bookId = textbook.getBookId();
        if (bookId == null)
        {
            throw BizErrors.badRequest("未指定图书");
        }

        // ① 图书必须存在（零外键，引用存在性在 service 校验）
        Book book = referenceGuard.requireExists(bookMapper, bookId, "图书");

        // ② 只有教材类图书才能建档 —— 非教材建了扩展行会让「非教材无行」的约定失效
        if (book.getBookType() == null || book.getBookType() != BOOK_TYPE_TEXTBOOK)
        {
            throw BizErrors.ruleViolated(
                    "「" + book.getTitle() + "」不是教材（图书类型为普通），不能维护教材扩展信息");
        }

        // ③ 1:1 判重：主键即外键，重复建档会直接撞主键，这里先给出可读的 409
        if (baseMapper.selectById(bookId) != null)
        {
            throw BizErrors.conflict("该图书已维护教材扩展信息，请直接修改");
        }

        if (textbook.getIsRequired() == null)
        {
            textbook.setIsRequired(1);
        }
        return baseMapper.insert(textbook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateBookTextbook(BookTextbook textbook)
    {
        if (textbook.getBookId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的图书");
        }
        referenceGuard.requireExists(baseMapper, textbook.getBookId(), "教材扩展信息");
        return baseMapper.updateById(textbook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeBookTextbooks(Long[] bookIds)
    {
        List<Long> idList = (bookIds == null) ? Collections.emptyList() : Arrays.asList(bookIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "教材扩展信息");
        // 物理删除：本表无 del_flag（见类注释）
        return baseMapper.deleteByIds(ids);
    }
}
