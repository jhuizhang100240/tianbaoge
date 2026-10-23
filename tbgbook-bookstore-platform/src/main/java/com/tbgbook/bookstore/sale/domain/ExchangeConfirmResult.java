package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/** 确认换货结果：单号/状态/换出换回金额/差额/逐行库存结存。 */
@Data
public class ExchangeConfirmResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long exchangeId;
    private String exchangeNo;
    private Integer status;
    private BigDecimal outAmount;
    private BigDecimal inAmount;

    /** 差额：正=补款 负=退款 */
    private BigDecimal diffAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime exchangeTime;

    /** 逐行库存结存描述 */
    private List<String> stockAfter;
}
