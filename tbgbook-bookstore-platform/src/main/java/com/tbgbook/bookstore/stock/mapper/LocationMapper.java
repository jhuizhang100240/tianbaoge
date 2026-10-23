package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.Location;

/**
 * 库位 Mapper 接口。
 *
 * @author tbgbook
 */
public interface LocationMapper extends BaseMapper<Location>
{
    /**
     * 查询库位列表。
     *
     * @param location 查询条件
     * @return 库位集合
     */
    List<Location> selectLocationList(Location location);
}
