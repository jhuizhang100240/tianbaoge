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

import com.tbgbook.bookstore.usedbook.domain.AcceptRequest;
import com.tbgbook.bookstore.usedbook.domain.Recycle;
import com.tbgbook.bookstore.usedbook.domain.RejectRequest;
import com.tbgbook.bookstore.usedbook.domain.SettleRequest;
import com.tbgbook.bookstore.usedbook.service.IUsedBookRecycleService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 二手书回收单 Controller（页面 A-41，流程二）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/usedbook/recycle")
public class UsedBookRecycleController extends BaseController
{
    private final IUsedBookRecycleService recycleService;

    /**
     * 查询回收单列表（可按单号 / 会员 / 状态 / 成色不一致 / 结算方式 / 预约日期与创建时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:list')")
    @GetMapping("/list")
    public TableDataInfo list(Recycle recycle)
    {
        startPage();
        List<Recycle> list = recycleService.selectRecycleList(recycle);
        return getDataTable(list);
    }

    /**
     * 获取回收单详情（含子表明细）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:list')")
    @GetMapping("/{recycleId}")
    public AjaxResult getInfo(@PathVariable("recycleId") Long recycleId)
    {
        return success(recycleService.selectRecycleById(recycleId));
    }

    /**
     * 新增回收单（单据头 + 子表同一事务；状态=待验收）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:add')")
    @Log(title = "二手书回收", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Recycle recycle)
    {
        return toAjax(recycleService.addRecycle(recycle));
    }

    /**
     * 修改回收单（仅待验收可改；传 items 时整体替换明细）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:edit')")
    @Log(title = "二手书回收", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Recycle recycle)
    {
        return toAjax(recycleService.updateRecycle(recycle));
    }

    /**
     * 删除回收单（仅待验收 / 已拒绝 / 已取消且未生成二手书副本的可删）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:remove')")
    @Log(title = "二手书回收", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recycleIds}")
    public AjaxResult remove(@PathVariable Long[] recycleIds)
    {
        return toAjax(recycleService.removeRecycles(recycleIds));
    }

    /**
     * 验收回收单（逐行判定成色；合格行自动生成二手书副本）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:accept')")
    @Log(title = "二手书回收验收", businessType = BusinessType.UPDATE)
    @PostMapping("/{recycleId}/accept")
    public AjaxResult accept(@PathVariable("recycleId") Long recycleId,
            @RequestBody AcceptRequest request)
    {
        return success(recycleService.accept(recycleId, request));
    }

    /**
     * 整单拒收（填写拒收原因；不生成副本、不结算）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:reject')")
    @Log(title = "二手书回收拒收", businessType = BusinessType.UPDATE)
    @PostMapping("/{recycleId}/reject")
    public AjaxResult reject(@PathVariable("recycleId") Long recycleId,
            @RequestBody RejectRequest request)
    {
        return toAjax(recycleService.reject(recycleId, request));
    }

    /**
     * 回收结算（现金登记；储值账户 / 积分联动写对应流水）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:recycle:settle')")
    @Log(title = "二手书回收结算", businessType = BusinessType.UPDATE)
    @PostMapping("/{recycleId}/settle")
    public AjaxResult settle(@PathVariable("recycleId") Long recycleId,
            @RequestBody SettleRequest request)
    {
        return success(recycleService.settle(recycleId, request));
    }
}
