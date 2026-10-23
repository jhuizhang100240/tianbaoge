package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/** 取货核销结果。 */
@Data
public class OrderPickupResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String orderNo;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pickupTime;

    private String pickupBy;
    private Integer pointGain;
}
