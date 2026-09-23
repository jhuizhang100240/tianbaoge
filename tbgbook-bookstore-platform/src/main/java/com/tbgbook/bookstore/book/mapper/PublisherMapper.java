package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.Publisher;

/**
 * 出版社 Mapper 接口。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供，**此处只声明 XML 覆盖不了的复杂查询**
 * （后端 CLAUDE.md §五：XML 只写 resultMap + 列表查询）。
 *
 * @author tbgbook
 */
public interface PublisherMapper extends BaseMapper<Publisher>
{
    /**
     * 查询出版社列表（含该社图书品种数）。
     *
     * @param publisher 查询条件
     * @return 出版社集合
     */
    List<Publisher> selectPublisherList(Publisher publisher);
}
