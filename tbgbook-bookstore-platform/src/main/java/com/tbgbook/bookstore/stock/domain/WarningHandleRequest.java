package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 处置库存预警的请求体（`PUT /inventory/warning/handle`）。
 *
 * <p>⚠️ <b>{@code handleStatus} 只接受 2已处理 / 3已忽略</b>（契约如此）——
 * 「1待处理」是扫描任务生成的初始态，不是可提交的处置结果。
 * 这里**不加 {@code @Min}/{@code @Max}**：契约把这个校验归到 **422 业务规则**而非 400 参数格式，
 * 且需要给出「只能填 2 或 3」这种带语义的文案；字段注解只能给通用的「取值非法」。
 * 必填（{@code @NotNull}）保留 —— 它缺失时确实是 400 参数缺失。
 *
 * @author tbgbook
 */
@Data
public class WarningHandleRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 预警ID（必填） */
    @NotNull(message = "预警ID不能为空")
    private Long warningId;

    /** 处理状态（2已处理 3已忽略）—— 取值合法性由 service 判定并返回 422 */
    @NotNull(message = "处理状态不能为空")
    private Integer handleStatus;

    /** 处理说明 */
    @Size(max = 500, message = "处理说明不能超过 500 个字符")
    private String handleRemark;

    /**
     * 是否生成补货入库单草稿，默认 false。
     *
     * <p>为 {@code true} 时按预警的 {@code bookId} 与 {@code suggestQty} 开一张
     * **采购入库单草稿**（不填库位，落 {@code locationId=0}），库位与实收数量由
     * A-16 开单页补齐。转单与处置在**同一事务**内完成。
     */
    private Boolean createStockIn;
}
