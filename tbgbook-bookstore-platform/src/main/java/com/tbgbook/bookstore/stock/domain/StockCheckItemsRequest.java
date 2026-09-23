package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

/**
 * 批量录入实盘数的请求体（`POST /inventory/stockcheck/{checkId}/items`）。
 *
 * <p>契约支持**增量提交**：可多次调用，已提交的行按 {@code itemId} 覆盖。
 * 故本请求体允许只带本次数的那些行（不必回传全部）。
 *
 * @author tbgbook
 */
@Data
public class StockCheckItemsRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 实盘明细数组 */
    @Valid
    @NotEmpty(message = "至少要录入一行实盘数")
    private List<StockCheckItemEntry> items;
}
