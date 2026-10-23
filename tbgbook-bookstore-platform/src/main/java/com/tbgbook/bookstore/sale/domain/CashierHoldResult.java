package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/** 收银台挂单结果（`POST /sales/cashier/hold` 的 {@code data}）：holdId / holdNo / holdTime。 */
@Data
public class CashierHoldResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long holdId;

    private String holdNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime holdTime;
}
