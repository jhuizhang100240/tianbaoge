package com.tbgbook.bookstore.app.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会员端会员中心聚合查询。
 */
@Mapper
public interface AppMemberMapper
{
    List<Map<String, Object>> selectPointLogs(@Param("memberId") Long memberId,
            @Param("changeType") Integer changeType, @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime, @Param("offset") int offset,
            @Param("limit") int limit);

    long countPointLogs(@Param("memberId") Long memberId, @Param("changeType") Integer changeType,
            @Param("beginTime") LocalDateTime beginTime, @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> selectBalanceLogs(@Param("memberId") Long memberId,
            @Param("amountType") Integer amountType, @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime, @Param("offset") int offset,
            @Param("limit") int limit);

    long countBalanceLogs(@Param("memberId") Long memberId, @Param("amountType") Integer amountType,
            @Param("beginTime") LocalDateTime beginTime, @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> selectTimeline(@Param("memberId") Long memberId,
            @Param("includeOrder") boolean includeOrder, @Param("includeBorrow") boolean includeBorrow,
            @Param("includePoint") boolean includePoint, @Param("includeRecycle") boolean includeRecycle,
            @Param("beginTime") LocalDateTime beginTime, @Param("endTime") LocalDateTime endTime,
            @Param("offset") int offset, @Param("limit") int limit);

    long countTimeline(@Param("memberId") Long memberId,
            @Param("includeOrder") boolean includeOrder, @Param("includeBorrow") boolean includeBorrow,
            @Param("includePoint") boolean includePoint, @Param("includeRecycle") boolean includeRecycle,
            @Param("beginTime") LocalDateTime beginTime, @Param("endTime") LocalDateTime endTime);

    Map<String, Object> selectTimelineSummary(@Param("memberId") Long memberId);

    List<Map<String, Object>> selectMessages(@Param("memberId") Long memberId,
            @Param("msgType") Integer msgType, @Param("readStatus") Integer readStatus,
            @Param("offset") int offset, @Param("limit") int limit);

    long countMessages(@Param("memberId") Long memberId, @Param("msgType") Integer msgType,
            @Param("readStatus") Integer readStatus);

    int countUnreadMessages(@Param("memberId") Long memberId);

    int markMessagesRead(@Param("memberId") Long memberId,
            @Param("messageIds") List<Long> messageIds);

    Map<String, Object> selectCancelBlockers(@Param("memberId") Long memberId);

    int incrementCouponIssued(@Param("templateId") Long templateId);
}
