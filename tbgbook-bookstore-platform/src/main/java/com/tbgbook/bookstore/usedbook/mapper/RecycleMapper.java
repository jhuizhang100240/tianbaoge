package com.tbgbook.bookstore.usedbook.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.usedbook.domain.Recycle;

/**
 * 二手书回收单 Mapper（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface RecycleMapper extends BaseMapper<Recycle>
{
    /**
     * 查询回收单列表。
     *
     * @param recycle 查询条件
     * @return 回收单集合
     */
    List<Recycle> selectRecycleList(Recycle recycle);
}
