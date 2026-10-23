package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Data;

/**
 * 发起二次盘点的请求体（`POST /inventory/stockcheck/{checkId}/recheck`）。
 *
 * @author tbgbook
 */
@Data
public class StockCheckRecheckRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 新盘点单的类型（1全盘 2抽盘），默认 2 抽盘。
     *
     * <p>二次盘点只盘上次的**差异行**，天然是抽盘 —— 默认值取 2 而不是原单的类型。
     */
    @Min(value = 1, message = "盘点类型取值 1 或 2")
    @Max(value = 2, message = "盘点类型取值 1 或 2")
    private Integer checkType;
}
