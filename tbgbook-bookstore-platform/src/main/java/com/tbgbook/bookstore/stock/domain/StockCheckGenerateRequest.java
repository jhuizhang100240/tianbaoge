package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 创建盘点单的请求体（`POST /inventory/stockcheck/generate`）。
 *
 * <p>三种范围（`scopeType`）决定把哪些「图书 + 库位」纳入盘点，
 * 并在这一刻**快照账面数**（见 {@link StockCheckItem} 的口径说明）。
 *
 * @author tbgbook
 */
@Data
public class StockCheckGenerateRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 盘点类型（1全盘 2抽盘） */
    @NotNull(message = "盘点类型不能为空")
    @Min(value = 1, message = "盘点类型取值 1 或 2")
    @Max(value = 2, message = "盘点类型取值 1 或 2")
    private Integer checkType;

    /** 范围类型（1按分类 2按库位 3指定图书） */
    @NotNull(message = "范围类型不能为空")
    @Min(value = 1, message = "范围类型取值 1~3")
    @Max(value = 3, message = "范围类型取值 1~3")
    private Integer scopeType;

    /** 范围值：分类ID / 库位ID / 图书ID列表（逗号分隔） */
    @NotNull(message = "范围值不能为空")
    @Size(max = 500, message = "范围值不能超过 500 个字符")
    private String scopeValue;

    /**
     * 是否显示账面数（true 明盘 / false 盲盘），**默认 false 即盲盘**（契约如此）。
     *
     * <p>⚠️ 与 §3.2 库存规则 4「盘点采用明盘 + 差异复核模式」有口径张力，
     * 已登记 `plan/待确认项.md`。此处按契约实现。
     */
    private Boolean showBookQty;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
