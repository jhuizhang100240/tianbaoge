package com.tbgbook.bookstore.sale.domain;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/** 教材取书核销结果：单号/累计已取/预订数/剩余数/状态 + 本次解锁的库存结存。 */
@Data
public class PickupResult
{
    private Long reserveId;
    private String reserveNo;

    /** 累计已取数量（含本次） */
    private Integer lockedQty;

    /** 预订数量 */
    private Integer qty;

    /** 剩余数量 = qty − lockedQty */
    private Integer remainQty;

    private Integer status;

    /** 本次释放的锁定库存结存描述 */
    private List<String> stockAfter;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pickupTime;
}
