package com.tbgbook.bookstore.usedbook.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.usedbook.domain.UsedBook;

/**
 * 二手书库存 Mapper（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface UsedBookMapper extends BaseMapper<UsedBook>
{
    /**
     * 查询二手书库存列表。
     *
     * @param usedBook 查询条件
     * @return 二手书集合
     */
    List<UsedBook> selectUsedBookList(UsedBook usedBook);
}
