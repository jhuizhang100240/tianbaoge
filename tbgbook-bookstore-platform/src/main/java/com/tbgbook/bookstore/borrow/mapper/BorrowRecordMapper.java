package com.tbgbook.bookstore.borrow.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.OverdueItem;

/**
 * 借阅单 Mapper（列表 / 逾期清单 / 统计走 XML）。
 *
 * @author tbgbook
 */
public interface BorrowRecordMapper extends BaseMapper<BorrowRecord>
{
    /**
     * 按条码命中「当前在借」记录（归还用）。
     *
     * @param code 图书条码 / ISBN
     * @return 借阅单；无则 null
     */
    BorrowRecord selectActiveByCode(@Param("code") String code);

    /**
     * 查询借阅单列表。
     *
     * @param record 查询条件
     * @return 借阅单集合
     */
    List<BorrowRecord> selectBorrowRecordList(BorrowRecord record);

    /**
     * 逾期清单（在借 / 已逾期，按应还时间升序）。
     *
     * @param memberId      限定会员（可空）
     * @param minOverdueDays 最小逾期天数（可空）
     * @param status        借阅状态（可空，默认 1/3）
     * @param now           应用侧当前时间（A32）
     * @return 逾期条目
     */
    List<OverdueItem> selectOverdueList(@Param("memberId") Long memberId,
            @Param("minOverdueDays") Integer minOverdueDays,
            @Param("status") Integer status, @Param("now") LocalDateTime now);

    /** 借出量趋势（按日/周/月） */
    List<Map<String, Object>> selectLendTrend(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("granularity") String granularity);

    /** 热门借阅图书 Top N */
    List<Map<String, Object>> selectTopBooks(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("limit") int limit);

    /** 借阅周期分布（借出 → 归还的整天数） */
    List<Map<String, Object>> selectPeriodDistribution(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 各级等级借出占比 */
    List<Map<String, Object>> selectLevelDistribution(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 窗口内逾期率（逾期单数 / 总借出单数） */
    Map<String, Object> selectOverdueRate(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 该会员当前在借册数（status in 1,3） */
    int countBorrowing(@Param("memberId") Long memberId);

    /** 在借单是否被他人预约（排队中 / 已到书待取） */
    int countActiveReserve(@Param("bookId") Long bookId);
}
