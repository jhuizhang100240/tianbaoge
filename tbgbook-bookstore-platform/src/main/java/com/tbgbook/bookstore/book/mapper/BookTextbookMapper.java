package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.BookTextbook;

/**
 * 教材扩展信息 Mapper 接口。
 *
 * @author tbgbook
 */
public interface BookTextbookMapper extends BaseMapper<BookTextbook>
{
    /**
     * 查询教材扩展信息列表。
     *
     * @param textbook 查询条件
     * @return 教材扩展信息集合
     */
    List<BookTextbook> selectBookTextbookList(BookTextbook textbook);
}
