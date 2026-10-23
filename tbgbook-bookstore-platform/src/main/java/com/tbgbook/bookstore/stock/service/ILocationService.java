package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.Location;

/**
 * 库位 服务层。
 *
 * @author tbgbook
 */
public interface ILocationService extends IService<Location>
{
    /**
     * 查询库位列表。
     *
     * @param location 查询条件
     * @return 库位集合
     */
    List<Location> selectLocationList(Location location);

    /**
     * 查询库位详情。
     *
     * @param locationId 库位ID
     * @return 库位；不存在时抛 404
     */
    Location selectLocationById(Long locationId);

    /**
     * 新增库位。编码在同一门店内唯一，重复返回 409。
     *
     * @param location 库位
     * @return 结果
     */
    int addLocation(Location location);

    /**
     * 修改库位。改编码同样要过唯一校验。
     *
     * @param location 库位
     * @return 结果
     */
    int updateLocation(Location location);

    /**
     * 删除库位。
     *
     * <p><b>被 {@code biz_stock.location_id} 引用的库位禁止删除</b>（422）：
     * 删掉会让库存行指向不存在的库位，而库存流水只增不删、历史流水会失去主语。
     * 正确做法是停用（{@code status=1}）。
     *
     * @param locationIds 需要删除的库位ID数组
     * @return 结果
     */
    int removeLocations(Long[] locationIds);
}
