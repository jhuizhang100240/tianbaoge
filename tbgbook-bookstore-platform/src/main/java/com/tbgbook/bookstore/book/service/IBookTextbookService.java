package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.BookTextbook;

/**
 * 教材扩展信息 Service 接口。
 *
 * @author tbgbook
 */
public interface IBookTextbookService extends IService<BookTextbook>
{
    /**
     * 查询教材扩展信息列表。
     *
     * @param textbook 查询条件
     * @return 教材扩展信息集合
     */
    List<BookTextbook> selectBookTextbookList(BookTextbook textbook);

    /**
     * 新增教材扩展信息。
     *
     * <p>1:1 约束：同一本书只能有一条；且只允许**教材类**图书（`book_type=2`）建档
     * （FR-4.1 规则 6「非教材无行」）。
     *
     * @param textbook 教材扩展信息
     * @return 影响行数
     */
    int addBookTextbook(BookTextbook textbook);

    /**
     * 修改教材扩展信息（主键为 bookId，不在路径）。
     *
     * @param textbook 教材扩展信息
     * @return 影响行数
     */
    int updateBookTextbook(BookTextbook textbook);

    /**
     * 批量删除教材扩展信息。
     *
     * <p>本表无 {@code del_flag}，删除为**物理删除** —— 它是图书的扩展属性，
     * 删掉不丢历史（历史在图书变更日志里）。
     *
     * @param bookIds 图书主键数组
     * @return 实际删除行数
     */
    int removeBookTextbooks(Long[] bookIds);
}
