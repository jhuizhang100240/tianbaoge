package com.tbgbook.bookstore.sale.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.sale.domain.CashierShift;
import com.tbgbook.bookstore.sale.service.ICashierShiftService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 收银班次台账 Controller（平台侧 URL {@code /system/shift}，A-33 日结对账的班次来源）。
 *
 * <p><b>权限串是 {@code system:shift:list} / {@code system:shift:remove}</b>，
 * 不走 {@code sales:} 前缀（G17 已裁决）—— 本端点给店长/管理员看班次台账与对账，
 * 与收银员自己在 {@code /sales/shift/{open,close,current}} 上的动作分开授权。
 *
 * <p><b>实现落在销售域</b>：类在 bookstore-platform 的 sale 包而非 tbgbook-system ——
 * 业务语义（交班现金差异、与日结的班次对应）全在销售域，增设跨模块依赖要动 pom。
 * 这是本次自定的位置例外，已登记 `plan/待确认项.md` A31。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/shift")
public class CashierShiftAdminController extends BaseController
{
    private final ICashierShiftService cashierShiftService;

    @PreAuthorize("@ss.hasPermi('system:shift:list')")
    @GetMapping("/list")
    public TableDataInfo list(CashierShift shift)
    {
        startPage();
        List<CashierShift> list = cashierShiftService.selectCashierShiftList(shift);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('system:shift:list')")
    @GetMapping("/{shiftId}")
    public AjaxResult getInfo(@PathVariable("shiftId") Long shiftId)
    {
        return success(cashierShiftService.selectCashierShiftById(shiftId));
    }

    /** 删除班次（**仅已交班且未被日结单引用**；进行中的要先交班）。 */
    @PreAuthorize("@ss.hasPermi('system:shift:remove')")
    @Log(title = "收银班次", businessType = BusinessType.DELETE)
    @DeleteMapping("/{shiftIds}")
    public AjaxResult remove(@PathVariable Long[] shiftIds)
    {
        return toAjax(cashierShiftService.removeCashierShifts(shiftIds));
    }
}
