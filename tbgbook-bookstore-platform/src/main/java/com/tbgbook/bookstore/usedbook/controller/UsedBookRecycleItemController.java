package com.tbgbook.bookstore.usedbook.controller;

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

import com.tbgbook.bookstore.usedbook.domain.RecycleItem;
import com.tbgbook.bookstore.usedbook.service.IUsedBookRecycleItemService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 二手书回收明细 Controller（页面 A-41、B-14）。
 *
 * <p>提交阶段只写「会员提交成色」；「店员判定成色 / 不一致标记 / 判定说明」由
 * 验收动作写入（{@code POST /usedbook/recycle/{id}/accept}）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/usedbook/recycle/item")
public class UsedBookRecycleItemController extends BaseController
{
    private final IUsedBookRecycleItemService recycleItemService;

    /**
     * 查询回收明细列表（可按回收单 / ISBN / 明细状态 / 成色不一致与创建时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:list')")
    @GetMapping("/list")
    public TableDataInfo list(RecycleItem item)
    {
        startPage();
        List<RecycleItem> list = recycleItemService.selectRecycleItemList(item);
        return getDataTable(list);
    }

    /**
     * 获取回收明细详情。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(recycleItemService.selectRecycleItemById(itemId));
    }

    /**
     * 新增回收明细（仅待验收单据可加）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:add')")
    @Log(title = "二手书回收明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody RecycleItem item)
    {
        return toAjax(recycleItemService.addRecycleItem(item));
    }

    /**
     * 修改回收明细（仅待验收单据 / 明细可改）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:edit')")
    @Log(title = "二手书回收明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody RecycleItem item)
    {
        return toAjax(recycleItemService.updateRecycleItem(item));
    }

    /**
     * 删除回收明细（仅待验收明细可删）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:remove')")
    @Log(title = "二手书回收明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(recycleItemService.removeRecycleItems(itemIds));
    }
}
