package com.tbgbook.bookstore.app.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * 会员端交易库存与订单查询 SQL。
 */
public interface AppTradeMapper
{
    Map<String, Object> selectStockForLock(@Param("bookId") Long bookId);

    int lockStock(@Param("stockId") Long stockId, @Param("qty") int qty);

    Map<String, Object> selectStockForRelease(@Param("bookId") Long bookId);

    int releaseStock(@Param("stockId") Long stockId, @Param("qty") int qty);

    int insertStockFlow(@Param("storeId") long storeId, @Param("bookId") Long bookId,
            @Param("locationId") Long locationId, @Param("stockType") int stockType,
            @Param("bizType") int bizType, @Param("bizNo") String bizNo,
            @Param("qtyChange") int qtyChange, @Param("qtyBefore") int qtyBefore,
            @Param("qtyAfter") int qtyAfter, @Param("createBy") String createBy,
            @Param("createTime") LocalDateTime createTime, @Param("remark") String remark);

    int lockUsedBook(@Param("usedBookId") Long usedBookId, @Param("orderId") Long orderId);

    int releaseUsedBook(@Param("usedBookId") Long usedBookId, @Param("orderId") Long orderId);

    List<Map<String, Object>> orderList(@Param("memberId") Long memberId, @Param("status") Integer status,
            @Param("offset") int offset, @Param("limit") int limit);

    long countOrderList(@Param("memberId") Long memberId, @Param("status") Integer status);

    Map<String, Object> orderDetail(@Param("orderId") Long orderId, @Param("memberId") Long memberId);

    List<Map<String, Object>> orderItems(@Param("orderId") Long orderId);

    List<Map<String, Object>> orderPayments(@Param("orderId") Long orderId);

    List<Map<String, Object>> expiredOrders(@Param("now") LocalDateTime now);
}
