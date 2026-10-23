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
     * <p>⚠️ <b>窗口边界必须来自应用侧（Java），不能用库的 {@code now()}</b>：
     * 本项目 JDBC URL 带 {@code sessionVariables=time_zone='+08:00'}，应用写入的戳是 +08；
     * 而 {@code mysql` CLI（会话时区 = SYSTEM = UTC）看到的 {@code now()} 是 UTC，两者差 8 小时
     * —— 本会话实测：{@code open_time}=18:38 而 {@code now()}=10:38，于是 {@code create_time <= now()}
     * 恒为假、班次聚合恒为 0 笔。这条曲线在 {@code docs/db/10_timezone_plus0800.sql} 里早有记载。
     * 故边界由调用方按 {@code LocalDateTime.now()} 传入，与写入同源。
     *
     * <p>上界由调用方**放宽 1 秒**（交班时传入 {@code now().plusSeconds(1)}），
     * 免得同一秒内写入的订单因秒精度截断被判在窗外。
     *
     * <p>⚠️ {@code biz_order} 没有班次列，故只能按时间窗算 —— 见 `DailySettlementMapper` 同类注释。
     */
    com.tbgbook.bookstore.sale.domain.SettlementAggregate aggregateShift(
            @Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}
