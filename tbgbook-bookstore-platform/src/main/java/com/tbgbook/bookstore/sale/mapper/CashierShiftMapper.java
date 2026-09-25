package com.tbgbook.bookstore.sale.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.CashierShift;

/** 收银员班次 Mapper（URL 前缀 `/system/shift`，但业务语义属销售域，见领域对象注释）。 */
public interface CashierShiftMapper extends BaseMapper<CashierShift>
{
    List<CashierShift> selectCashierShiftList(CashierShift shift);

    /** 某收银员某日某编码的班次（唯一性校验用）。 */
    CashierShift selectByUserDateCode(@Param("userId") Long userId,
            @Param("shiftDate") LocalDate shiftDate,
            @Param("shiftCode") String shiftCode);

    /** 某收银员**未交班**的历史班次（开班前置校验：不许同时开着两个班）。 */
    List<CashierShift> selectOpenByUser(@Param("userId") Long userId);

    /**
     * 某班次窗口内的收款聚合（交班时回填 {@code orderCount} / {@code totalAmount}，
     * 并算出**系统现金收款**用于现金差异）。
     *
     * <p>窗口用班次的实际 {@code openTime} ~ {@code closeTime}（交班时传入当刻）。
     * ⚠️ {@code biz_order} 没有班次列，故只能按时间窗算 —— 见 `DailySettlementMapper` 同类注释。
     */
    com.tbgbook.bookstore.sale.domain.SettlementAggregate aggregateShift(
            @Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}
