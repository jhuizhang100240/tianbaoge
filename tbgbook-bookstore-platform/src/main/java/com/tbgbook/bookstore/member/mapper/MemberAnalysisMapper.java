package com.tbgbook.bookstore.member.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.tbgbook.bookstore.member.domain.aggregate.MemberTimelineEntry;

/**
 * 会员全景 / 时间轴 / 画像的聚合查询 Mapper（阶段五步骤③c-3）。
 *
 * <p>全部为只读跨表聚合，走 XML；SQL 一律 {@code #{}} 参数化（NFR-S6）。
 * 时间窗口边界由**应用侧**传入（A32 同口径），不在 SQL 里用 {@code now()} 与
 * 库内 datetime 比较（`10_timezone_plus0800.sql` 已记载应用与 CLI 时区不同源）。
 *
 * @author tbgbook
 */
public interface MemberAnalysisMapper
{
    /**
     * 窗口内消费统计（已完成且非红冲订单）。
     *
     * @param memberId 会员ID
     * @param from     窗口起点（含）
     * @param to       窗口终点（含）
     * @return 行 map：orderCount / totalAmount / grossMargin / activeDays / lastOrderTime
     */
    Map<String, Object> selectConsumeStat(@Param("memberId") Long memberId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 窗口内品类偏好（按金额倒序）。
     *
     * @return 行 map：categoryId / categoryName / amount
     */
    List<Map<String, Object>> selectCategoryPreference(@Param("memberId") Long memberId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 窗口内活跃时段分布（4 段 + 未知）。
     *
     * @return 行 map：period / cnt
     */
    List<Map<String, Object>> selectActivePeriods(@Param("memberId") Long memberId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 窗口内每笔订单的实付金额（客单价分布用）。
     *
     * @return 实付金额列表
     */
    List<java.math.BigDecimal> selectOrderAmounts(@Param("memberId") Long memberId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * RFM 同店样本（窗口内有已完成订单的会员）。
     *
     * @return 行 map：memberId / recencyDays / orderCount / monetary
     */
    List<Map<String, Object>> selectRfmSamples(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("now") LocalDateTime now);

    /**
     * 借阅统计：在借（1/3）/ 逾期（3）/ 历史总数。
     *
     * @return 行 map：borrowing / overdue / total
     */
    Map<String, Object> selectBorrowStat(@Param("memberId") Long memberId);

    /**
     * 窗口内即将到期的积分合计（含明天、不含今天）。
     *
     * @param withinDays 提醒窗口天数
     * @return 合计积分；无则 0
     */
    Integer selectExpiringPoints(@Param("memberId") Long memberId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 会员时间轴总数（按 types 过滤；types 传 null / 空 = 全部）。
     *
     * @param typesCsv 逗号分隔类型串，如 `order,borrow`
     */
    Long countTimeline(@Param("memberId") Long memberId, @Param("typesCsv") String typesCsv);

    /**
     * 会员时间轴分页（ORDER BY time DESC, bizNo DESC）。
     *
     * @param typesCsv 逗号分隔类型串
     * @param limit    每页条数
     * @param offset   偏移
     */
    List<MemberTimelineEntry> selectTimeline(@Param("memberId") Long memberId,
            @Param("typesCsv") String typesCsv, @Param("limit") int limit,
            @Param("offset") int offset);
}
