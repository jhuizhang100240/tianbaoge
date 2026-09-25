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

import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.sale.service.ICouponTemplateService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 优惠券模板 Controller（页面 A-37，FR-4.3.8）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/coupon-template")
public class CouponTemplateController extends BaseController
{
    private final ICouponTemplateService couponTemplateService;

    /**
     * 查询优惠券模板列表。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:list')")
    @GetMapping("/list")
    public TableDataInfo list(CouponTemplate template)
    {
        startPage();
        List<CouponTemplate> list = couponTemplateService.selectCouponTemplateList(template);
        return getDataTable(list);
    }

    /**
     * 获取优惠券模板详情。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:list')")
    @GetMapping("/{templateId}")
    public AjaxResult getInfo(@PathVariable("templateId") Long templateId)
    {
        return success(couponTemplateService.selectCouponTemplateById(templateId));
    }

    /**
     * 新增优惠券模板（**门槛须 ≥ 面额**；有效期参数按类型校验）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:add')")
    @Log(title = "优惠券模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody CouponTemplate template)
    {
        return toAjax(couponTemplateService.addCouponTemplate(template));
    }

    /**
     * 修改优惠券模板（编号、状态与统计四列不接受写入）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:edit')")
    @Log(title = "优惠券模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CouponTemplate template)
    {
        return toAjax(couponTemplateService.updateCouponTemplate(template));
    }

    /**
     * 删除优惠券模板（**已被券实例引用时只能停用**）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:remove')")
    @Log(title = "优惠券模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{templateIds}")
    public AjaxResult remove(@PathVariable Long[] templateIds)
    {
        return toAjax(couponTemplateService.removeCouponTemplates(templateIds));
    }
}
