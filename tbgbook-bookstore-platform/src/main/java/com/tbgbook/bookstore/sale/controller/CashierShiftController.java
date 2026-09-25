package com.tbgbook.bookstore.sale.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.sale.domain.ShiftCloseRequest;
import com.tbgbook.bookstore.sale.domain.ShiftOpenRequest;
import com.tbgbook.bookstore.sale.service.ICashierShiftService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 收银班次 Controller（A-28 收银台，FR-4.3.12）。
 *
 * <p><b>权限串仍是 sales:cashier</b>（与收银台同一菜单 2301）—— 开班/交班是收银员在收银台上的动作，
 * 独立成菜单反而多一层授权。⚠️ 注意与 {@code /system/shift/*} 区分：那两个端点是**店长/管理员
 * 看班次台账**，权限串是 {@code system:shift:*}（G17 已裁决），走 {@link CashierShiftAdminController}。
 *
 * <p><b>未关联班次的用户即使有收银菜单权限也无法开班收银</b>（契约原文）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/shift")
public class CashierShiftController extends BaseController
{
    private final ICashierShiftService cashierShiftService;

    /** 开班（同一收银员同一日同一班次不可重复；不允许同时开着两个班）。 */
    @PreAuthorize("@ss.hasPermi('sales:cashier:open')")
    @Log(title = "收银开班", businessType = BusinessType.INSERT)
    @PostMapping("/open")
    public AjaxResult open(@Valid @RequestBody ShiftOpenRequest request)
    {
        return success(cashierShiftService.open(request));
    }

    /** 交班并实点现金；返回**现金差异**（实点 − 系统现金收款）。 */
    @PreAuthorize("@ss.hasPermi('sales:cashier:close')")
    @Log(title = "收银交班", businessType = BusinessType.UPDATE)
    @PostMapping("/close")
    public AjaxResult close(@Valid @RequestBody ShiftCloseRequest request)
    {
        return success(cashierShiftService.close(request));
    }

    /** 查询**当前登录收银员**进行中的班次；无班次返回 null（前端据此引导开班）。 */
    @PreAuthorize("@ss.hasPermi('sales:cashier:list')")
    @GetMapping("/current")
    public AjaxResult current()
    {
        return success(cashierShiftService.current());
    }
}
