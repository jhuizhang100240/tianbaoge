package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 处置库存预警的结果（`PUT /inventory/warning/handle` 的 {@code data}）。
 *
 * <p>契约要求 {@code handleStatus/handleBy/handleTime}，
 * {@code createStockIn=true} 时附带 {@code stockInId/stockInNo}。
 *
 * <p>{@code stockInId}/{@code stockInNo} 标了 {@link JsonInclude.Include#NON_NULL}
 * —— 不转单时这两个字段**整条不出现**在响应里，而不是出现为 null。
 * 与图书标签打印的 `includePrice=false 时 price 不出现` 是同一处置：
 * 「没发生这件事」比「发生了但值为空」对前端更明确。
 *
 * @author tbgbook
 */
@Data
public class WarningHandleResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 预警ID */
    private Long warningId;

    /** 处置后的状态（2已处理 3已忽略） */
    private Integer handleStatus;

    /** 处理人 */
    private String handleBy;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    /** 生成的补货入库单ID（未转单时不出现） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long stockInId;

    /** 生成的补货入库单号（未转单时不出现） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String stockInNo;
}
