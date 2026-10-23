package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 录入实盘数的单行（`POST /inventory/stockcheck/{checkId}/items` 的数组元素）。
 *
 * <p><b>只有 {@code actualQty} 与两段文字是客户端给的</b>：
 * {@code bookQty} / {@code diffQty} / {@code diffAmount} / {@code diffType} /
 * {@code isPeriodDiff} 全部由服务端算 —— 它们是判定盈亏与责任归属的依据，
 * 让客户端填等于让被盘点的人自己写差异（契约也明写「不接受传入」）。
 *
 * @author tbgbook
 */
@Data
public class StockCheckItemEntry implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 盘点明细ID */
    @NotNull(message = "盘点明细ID不能为空")
    private Long itemId;

    /** 实盘数 */
    @NotNull(message = "实盘数不能为空")
    @Min(value = 0, message = "实盘数不能为负")
    private Integer actualQty;

    /** 差异原因（差异行必填，服务端校验） */
    @Size(max = 200, message = "差异原因不能超过 200 个字符")
    private String reason;

    /** 处理建议 */
    @Size(max = 200, message = "处理建议不能超过 200 个字符")
    private String suggestion;
}
