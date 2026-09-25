package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.CashierShift;
import com.tbgbook.bookstore.sale.domain.ShiftCloseRequest;
import com.tbgbook.bookstore.sale.domain.ShiftCloseResult;
import com.tbgbook.bookstore.sale.domain.ShiftOpenRequest;

/**
 * 收银员班次 Service（A-28，FR-4.3.12）。
 *
 * <p><b>未关联班次的用户即使有收银菜单权限也无法开班收银</b>（契约原文）。
 * URL 前缀是 {@code /system/shift}（平台侧，权限串 {@code system:shift:*}），
 * 但业务语义属销售域，故实现落在这里 —— 已登记 A31。
 */
public interface ICashierShiftService extends IService<CashierShift>
{
    List<CashierShift> selectCashierShiftList(CashierShift shift);

    CashierShift selectCashierShiftById(Long shiftId);

    /**
     * 开班。
     *
     * @throws com.tbgbook.common.exception.ServiceException
     *         409 该收银员当日该班次已开班 / 存在未交班的历史班次
     */
    CashierShift open(ShiftOpenRequest request);

    /**
     * 交班并实点现金；系统现金收款与实点不符时返回 {@code cashDiff}。
     *
     * @throws com.tbgbook.common.exception.ServiceException 404 班次不存在；409 已交班
     */
    ShiftCloseResult close(ShiftCloseRequest request);

    /** 查询**当前登录收银员**进行中的班次；无班次返回 {@code null}（前端据此引导开班）。 */
    CashierShift current();

    /** 删除班次（**仅已交班**；进行中的班次要先交班 —— 需求点名的删除保护）。 */
    int removeCashierShifts(Long[] shiftIds);
}
