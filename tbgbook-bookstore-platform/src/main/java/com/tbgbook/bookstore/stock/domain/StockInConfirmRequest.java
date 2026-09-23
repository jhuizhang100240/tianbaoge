package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 确认入库的请求体（`POST /inventory/stockin/{inId}/confirm`）。
 *
 * <p>契约里只有一个选填的 {@code inTime}（入库时间，默认当前时间）——
 * 现实中会出现「货是昨天到的、今天才补录」，所以允许回填。
 * 整个请求体可省略，此时按当前时间入库。
 *
 * @author tbgbook
 */
@Data
public class StockInConfirmRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 入库时间，留空取当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inTime;
}
