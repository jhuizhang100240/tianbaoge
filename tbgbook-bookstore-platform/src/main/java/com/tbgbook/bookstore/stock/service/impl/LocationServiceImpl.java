package com.tbgbook.bookstore.stock.service.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.Location;
import com.tbgbook.bookstore.stock.mapper.LocationMapper;
import com.tbgbook.bookstore.stock.service.ILocationService;
import com.tbgbook.common.utils.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 库位 服务层实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class LocationServiceImpl extends ServiceImpl<LocationMapper, Location> implements ILocationService
{
    /** 引用库位的表与列（删除保护用） */
    private static final String REF_TABLE_STOCK = "biz_stock";

    private final ReferenceGuard referenceGuard;

    @Override
    public List<Location> selectLocationList(Location location)
    {
        return baseMapper.selectLocationList(location);
    }

    @Override
    public Location selectLocationById(Long locationId)
    {
        return referenceGuard.requireExists(baseMapper, locationId, "库位");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addLocation(Location location)
    {
        requireCodeUnique(location.getLocationCode(), null);
        if (StringUtils.isBlank(location.getStatus()))
        {
            location.setStatus("0");
        }
        return baseMapper.insert(location);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateLocation(Location location)
    {
        Long locationId = location.getLocationId();
        if (locationId == null)
        {
            throw BizErrors.badRequest("未指定要修改的库位");
        }
        referenceGuard.requireExists(baseMapper, locationId, "库位");
        requireCodeUnique(location.getLocationCode(), locationId);
        return baseMapper.updateById(location);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeLocations(Long[] locationIds)
    {
        Collection<? extends Serializable> ids = referenceGuard.requireIds(Arrays.asList(locationIds), "库位");
        for (Serializable id : ids)
        {
            Location current = referenceGuard.requireExists(baseMapper, id, "库位");
            // ⚠️ biz_stock 没有 del_flag，故 softDeleteColumn 传 null
            // ⚠️ 参数顺序是 (refLabel 引用方, selfLabel 被删对象) —— 传反了文案会变成
            //    「该库存已被 N 条库位引用」
            referenceGuard.requireNoReference(REF_TABLE_STOCK, "location_id", id,
                    null, "库存", "库位", "改为停用");
        }
        return baseMapper.deleteByIds(ids);
    }

    /**
     * 库位编码在同一门店内唯一（{@code uk_store_code}）。
     *
     * <p>先查再插有竞态窗口，唯一索引是最后一道兜底 —— 与图书的 ISBN 同一口径。
     *
     * @param code      待校验的编码
     * @param excludeId 修改时排除自身；新增传 {@code null}
     */
    private void requireCodeUnique(String code, Long excludeId)
    {
        if (StringUtils.isBlank(code))
        {
            return;
        }
        Long count = baseMapper.selectCount(Wrappers.<Location>lambdaQuery()
                .eq(Location::getLocationCode, code)
                .ne(excludeId != null, Location::getLocationId, excludeId));
        if (count != null && count > 0)
        {
            throw BizErrors.duplicate("库位", "编码", code);
        }
    }
}
