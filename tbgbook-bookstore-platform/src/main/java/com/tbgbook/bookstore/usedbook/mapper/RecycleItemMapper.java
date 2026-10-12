package com.tbgbook.bookstore.usedbook.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.usedbook.domain.RecycleItem;

/**
 * 二手书回收明细 Mapper（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface RecycleItemMapper extends BaseMapper<RecycleItem>
{
    /**
     * 查询回收明细列表。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<RecycleItem> selectRecycleItemList(RecycleItem item);
}
