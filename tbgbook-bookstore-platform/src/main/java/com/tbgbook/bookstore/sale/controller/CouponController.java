package com.tbgbook.bookstore.sale.controller;

import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.CouponGrantRequest;
import com.tbgbook.bookstore.sale.service.ICouponService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会员优惠券实例 Controller（页面 A-38，FR-4.3.8）。
 *
 * <p><b>核销不开放手工接口</b>（契约明写）：{@code 1未使用→2已使用} 由收银结算内部完成。
 * 本控制器的增改只服务「后台补录」。
 *
 * <p>⚠️ <b>{@code GET /{couponId}} 与 {@code GET /verify} 同为 3 段</b>：
 * 两者**都在本控制器里**，Spring 的字面量模式优先于路径变量，故 {@code /verify}
 * 不会被解析成 {@code couponId="verify"}（那还会因 {@code Long} 转换失败报 400）。
 * 放在同一个控制器里正是为了让这条优先级**只有单一出处**。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/coupon")
public class CouponController extends BaseController
{
    private final ICouponService couponService;

    /**
     * 查询会员优惠券实例列表（按会员筛选时会顺带惰性刷新该会员的过期状态）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:list')")
    @GetMapping("/list")
    public TableDataInfo list(Coupon coupon)
    {
        startPage();
        List<Coupon> list = couponService.selectCouponList(coupon);
        return getDataTable(list);
    }

    /**
     * 校验券对当前购物车是否可用（收银选券前预校验）。
     *
     * <p>⚠️ 只判「是否本人持有 / 未使用 / 未过期 / 门槛够不够」；
     * 「范围不符」与「与特价商品冲突」需要购物车明细，在**试算**里判（见 `CouponVerifyResult`）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:list')")
    @GetMapping("/verify")
    public AjaxResult verify(@RequestParam("couponId") Long couponId,
            @RequestParam("memberId") Long memberId,
            @RequestParam("amount") BigDecimal amount)
    {
        return success(couponService.verify(couponId, memberId, amount));
    }

    /**
     * 获取会员优惠券实例详情（顺带惰性刷新该券的过期状态）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:list')")
    @GetMapping("/{couponId}")
    public AjaxResult getInfo(@PathVariable("couponId") Long couponId)
    {
        return success(couponService.selectCouponById(couponId));
    }

    /**
     * 新增会员优惠券实例（**后台补录**；面额/门槛/有效期/券名全部从模板快照）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:add')")
    @Log(title = "会员优惠券", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Coupon coupon)
    {
        return toAjax(couponService.addCoupon(coupon));
    }

    /**
     * 修改会员优惠券实例（**仅未使用可改**，且只能改持有人等元数据）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:edit')")
    @Log(title = "会员优惠券", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Coupon coupon)
    {
        return toAjax(couponService.updateCoupon(coupon));
    }

    /**
     * 删除会员优惠券实例（**仅未使用可删**；已使用的是订单抵扣的凭据）。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:remove')")
    @Log(title = "会员优惠券", businessType = BusinessType.DELETE)
    @DeleteMapping("/{couponIds}")
    public AjaxResult remove(@PathVariable Long[] couponIds)
    {
        return toAjax(couponService.removeCoupons(couponIds));
    }

    /**
     * 人工补发优惠券（**敏感操作**，强制记操作日志；补发原因必填）。
     *
     * <p>**部分失败不整体回滚**：已达每人限领的人记进 {@code failDetails} 后照常提交 ——
     * 让一个人失败连带其余人拿不到券，对运营不可接受。
     */
    @PreAuthorize("@ss.hasPermi('sales:coupon:grant')")
    @Log(title = "优惠券补发", businessType = BusinessType.INSERT)
    @PostMapping("/grant")
    public AjaxResult grant(@Valid @RequestBody CouponGrantRequest request)
    {
        return success(couponService.grant(request));
    }
}
