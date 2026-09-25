package com.tbgbook.bookstore.sale.controller;

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

import com.tbgbook.bookstore.sale.domain.CouponBatchExecuteRequest;
import com.tbgbook.bookstore.sale.domain.CouponGrantBatch;
import com.tbgbook.bookstore.sale.service.ICouponGrantBatchService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 优惠券发放批次 Controller（页面 A-38，FR-4.3.8）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/coupon-batch")
public class CouponGrantBatchController extends BaseController
{
    private final ICouponGrantBatchService couponGrantBatchService;

    /**
     * 查询优惠券发放批次列表。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:list')")
    @GetMapping("/list")
    public TableDataInfo list(CouponGrantBatch batch)
    {
        startPage();
        List<CouponGrantBatch> list = couponGrantBatchService.selectCouponGrantBatchList(batch);
        return getDataTable(list);
    }

    /**
     * 获取优惠券发放批次详情（发放进度看 {@code actualQty}/{@code receivedQty}）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:list')")
    @GetMapping("/{batchId}")
    public AjaxResult getInfo(@PathVariable("batchId") Long batchId)
    {
        return success(couponGrantBatchService.selectCouponGrantBatchById(batchId));
    }

    /**
     * 新增优惠券发放批次（状态恒为 1发放中，批次号服务端生成）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:add')")
    @Log(title = "优惠券发放批次", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody CouponGrantBatch batch)
    {
        return toAjax(couponGrantBatchService.addCouponGrantBatch(batch));
    }

    /**
     * 修改优惠券发放批次（**仅未执行的批次可改**）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:edit')")
    @Log(title = "优惠券发放批次", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CouponGrantBatch batch)
    {
        return toAjax(couponGrantBatchService.updateCouponGrantBatch(batch));
    }

    /**
     * 删除优惠券发放批次（**已执行完成的不可删**）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:remove')")
    @Log(title = "优惠券发放批次", businessType = BusinessType.DELETE)
    @DeleteMapping("/{batchIds}")
    public AjaxResult remove(@PathVariable Long[] batchIds)
    {
        return toAjax(couponGrantBatchService.removeCouponGrantBatches(batchIds));
    }

    /**
     * 执行发放（按人群类型圈选 → 校验模板与总量 → 逐人发券 → 置 2已完成）。
     *
     * <p>请求体可省略（{@code grantType} 是唯一字段且选填，不传则沿用批次上的值）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:execute')")
    @Log(title = "优惠券发放批次", businessType = BusinessType.UPDATE)
    @PostMapping("/{batchId}/execute")
    public AjaxResult execute(@PathVariable("batchId") Long batchId,
            @Valid @RequestBody(required = false) CouponBatchExecuteRequest request)
    {
        return success(couponGrantBatchService.execute(batchId,
                request == null ? null : request.getGrantType()));
    }
}
