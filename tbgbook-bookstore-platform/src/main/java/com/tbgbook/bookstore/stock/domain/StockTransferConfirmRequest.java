package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 确认调拨的请求体（`POST /inventory/transfer/{transferId}/confirm`）。
 *
 * <p>契约里只有一个选填的 {@code transferTime}（调拨时间，默认当前时间）——
 * 现实中会出现「书是上午挪的、下午才补录」，所以允许回填。
 * 整个请求体可省略，此时按当前时间调拨。
 *
 * @author tbgbook
 */
@Data
public class StockTransferConfirmRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 调拨时间，留空取当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transferTime;
}
