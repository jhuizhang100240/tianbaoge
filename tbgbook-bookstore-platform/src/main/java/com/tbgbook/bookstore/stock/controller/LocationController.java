package com.tbgbook.bookstore.stock.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.Location;
import com.tbgbook.bookstore.stock.service.ILocationService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 库位 Controller（页面 A-25 库位管理）。
 *
 * <p>相对代码生成器产物的**人工裁剪点**：① 删掉 export 端点（文档里只有 5 个端点）；
 * ② 详情权限串 {@code :query} → {@code :list}；③ 删除走带删除保护的 Service 方法；
 * ④ 补 {@code @Validated} / {@code @Valid}（NFR-M2）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/location")
public class LocationController extends BaseController
{
    private final ILocationService locationService;

    /**
     * 查询库位列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:location:list')")
    @GetMapping("/list")
    public TableDataInfo list(Location location)
    {
        startPage();
        List<Location> list = locationService.selectLocationList(location);
        return getDataTable(list);
    }

    /**
     * 获取库位详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:location:list')")
    @GetMapping("/{locationId}")
    public AjaxResult getInfo(@PathVariable("locationId") Long locationId)
    {
        return success(locationService.selectLocationById(locationId));
    }

    /**
     * 新增库位。编码在同一门店内唯一，重复返回 409。
     */
    @PreAuthorize("@ss.hasPermi('inventory:location:add')")
    @Log(title = "库位", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Location location)
    {
        return toAjax(locationService.addLocation(location));
    }

    /**
     * 修改库位（主键在请求体的 {@code locationId}，不在路径）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:location:edit')")
    @Log(title = "库位", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Location location)
    {
        return toAjax(locationService.updateLocation(location));
    }

    /**
     * 删除库位。
     *
     * <p>被 {@code biz_stock.location_id} 引用的库位返回 422，提示改为停用。
     */
    @PreAuthorize("@ss.hasPermi('inventory:location:remove')")
    @Log(title = "库位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{locationIds}")
    public AjaxResult remove(@PathVariable Long[] locationIds)
    {
        return toAjax(locationService.removeLocations(locationIds));
    }
}
