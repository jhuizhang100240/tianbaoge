package com.tbgbook.bookstore.sale.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.DailySettlement;
import com.tbgbook.bookstore.sale.domain.SettlementAggregate;

/** 日结对账 Mapper。 */
public interface DailySettlementMapper extends BaseMapper<DailySettlement>
{
    List<DailySettlement> selectDailySettlementList(DailySettlement settlement);

    /** 取下一个自增主键（用于预生成 settleNo；`settle_no` 是 NOT NULL + 唯一索引）。 */
    Long selectNextSettleId();

    /**
     * 给窗口内的订单挂上日结单号（生成时调用一次）。
     *
     * <p>⚠️ {@code biz_order} **没有任何班次列**（实测确认），故窗口只能落在时间区间内。
     * <b>边界由调用方按应用侧时间传入</b>（不能用库的 {@code now()}：库是 UTC、应用是 +08，
     * 差 8 小时会让窗口恒为空，详见 docs/db/10_timezone_plus0800.sql）。
     *
     * @return 本次挂上的订单数
     */
    int attachOrders(@Param("settleId") Long settleId, @Param("storeId") Long storeId,
            @Param("begin") java.time.LocalDateTime begin,
            @Param("end") java.time.LocalDateTime end);

    /**
     * 聚合某张日结单下订单的收款与退款（**一次 SQL 出全部汇总列**）。
     *
     * <p>窗口口径（本次自定，见 A31）：优先用班次的**实际开交班时间**
     * （{@code shiftId} 非空且班次存在）—— 班次跨零点时用「日期 00:00 ~ 次日 00:00」会漏；
     * 没给班次ID时退化为「{@code settleDate} 当天」。
     *
     * <p>只统计**未取消**的订单（{@code status &lt;&gt; 5}）：取消单没收到钱，
     * 计进收款额会让日结对不上银行流水。
     *
     * @param shiftName 班次名（{@code biz_cashier_shift.shift_name} 与订单的对应关系）
     * @param begin     窗口起
     * @param end       窗口止
     * @return 汇总结果；无数据时各列均为 0（SQL 里 ifnull 兜底），不会返回 null
     */
    SettlementAggregate aggregate(@Param("settleId") Long settleId);

    /**
     * 把订单上的日结单号摘掉（删除日结单时调用）。
     *
     * <p>不摘的话那些订单会永远挂在一张**已不存在的**日结单上 —— 按 `settle_id` 聚合时
     * 它们既不属任何日结单、也不会被新的日结单收进来（`attachOrders` 只挑 `settle_id = 0` 的）。
     *
     * @return 被摘掉的订单数
     */
    int detachOrders(@Param("settleIds") Long[] settleIds);

    /**
     * 锁定窗口内的订单（置 {@code is_settled=1}）。
     *
     * <p>订单的改删校验本来就看 {@code is_settled}，故「锁定日结」的落地方式就是批量置位 ——
     * 不新增字段、不改订单的既有规则。
     *
     * @return 被锁定的订单数
     */
    int lockOrders(@Param("settleId") Long settleId);
}
