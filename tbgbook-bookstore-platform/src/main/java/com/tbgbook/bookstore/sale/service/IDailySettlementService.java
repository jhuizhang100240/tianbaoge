package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.DailySettlement;
import com.tbgbook.bookstore.sale.domain.SettlementGenerateRequest;
import com.tbgbook.bookstore.sale.domain.SettlementLockResult;

/**
 * 日结对账 Service（A-33，FR-4.3.12）。
 *
 * <p><b>各金额列一律由订单聚合生成，不接受传入</b>（契约明写）：唯一的维护点是
 * {@link #generate}，重复生成覆盖未锁定的单。
 *
 * <p><b>锁定 = 订单不可再改</b>：{@link #lock} 给窗口内的订单置 {@code is_settled=1}
 * （订单的改删校验本来就看这一列），如需调整只能走红冲（§4.3 规则 6）。
 *
 * <p><b>窗口口径（本次自定，见 A31）</b>：优先用班次的**实际开交班时间**
 * （给了 {@code shiftId} 且班次存在）；否则退化为「{@code settleDate} 当天」。
 * ⚠️ {@code biz_order} 没有任何班次列，所以班次与订单的对应只能靠时间窗表达。
 */
public interface IDailySettlementService extends IService<DailySettlement>
{
    List<DailySettlement> selectDailySettlementList(DailySettlement settlement);

    DailySettlement selectDailySettlementById(Long settleId);

    /**
     * 新增日结单（**只建空单**）。
     *
     * <p>金额列是聚合出来的，新建时一律置 0；真正的汇总走 {@link #generate}。
     * 「新增」这个端点存在只是为了满足契约的标准 CRUD 形态 —— 日常用法是直接 generate。
     */
    int addDailySettlement(DailySettlement settlement);

    /**
     * 生成日结对账（**重复生成覆盖未锁定的单**）。
     *
     * @throws com.tbgbook.common.exception.ServiceException
     *         409 该日期该班次已锁定；422 该班次无收款记录
     */
    DailySettlement generate(SettlementGenerateRequest request);

    /**
     * 锁定日结：**该批次订单不可再修改**（红冲除外），锁定不可逆。
     *
     * @param diffReason 差异说明（{@code diffAmount != 0} 时**必填**，契约错误码原文）
     * @throws com.tbgbook.common.exception.ServiceException 422 有差异却未填说明；409 已锁定
     */
    SettlementLockResult lock(Long settleId, String diffReason);

    /** 修改日结（**仅 1待对账**；只接受备注与差异说明，金额列不接受传入）。 */
    int updateDailySettlement(DailySettlement settlement);

    /** 删除日结（**仅 1待对账**；已锁定的不可删）。 */
    int removeDailySettlements(Long[] settleIds);
}
