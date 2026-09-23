package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 图书批量导入明细行对象 biz_book_import_row
 *
 * <p><b>由解析阶段生成，执行阶段只读</b>。表里没有审计五字段、没有 {@code remark}、
 * 没有 {@code del_flag}，因此**不继承 {@code BaseEntity}** —— 继承会让 MP 去写不存在的列。
 *
 * <p>本表也没有按时间区间筛选的需求（列表筛选只有 {@code batchId} / {@code rowStatus} /
 * {@code isbn}），故**不需要**像 {@code BookPriceHistory} 那样补瞬态 {@code params}。
 *
 * <p><b>{@code rawData} 是 MySQL {@code json} 列</b>，接口文档声明其类型为
 * {@code object}，因此用 MP 的 {@link JacksonTypeHandler} 做 Map ↔ json 双向转换。
 * ⚠️ 必须同时标 {@code autoResultMap = true}，否则 MP 生成的默认 resultMap 不会带上
 * typeHandler，读出来是字符串而不是对象。
 *
 * <p><b>{@code rawData} 是执行阶段的唯一数据来源</b>：解析时把整行原样存进来，
 * 执行时直接从库里读回，**不再二次读取 Excel 文件** —— 这样解析与执行之间
 * 即便文件被删除/替换，导入的仍是操作员在步骤③确认过的那份数据。
 *
 * @author tbgbook
 */
@Data
@TableName(value = "biz_book_import_row", autoResultMap = true)
public class BookImportRow implements Serializable
{
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 行状态取值（接口文档「图书批量导入明细行」字段字典）
    // ------------------------------------------------------------------

    /** 行状态：可导入 */
    public static final int ROW_VALID = 1;

    /** 行状态：警告（可导入，但有需要留意的信息） */
    public static final int ROW_WARN = 2;

    /** 行状态：错误（不可导入） */
    public static final int ROW_ERROR = 3;

    /** 行ID */
    @TableId(value = "row_id", type = IdType.AUTO)
    private Long rowId;

    /** 批次ID */
    @NotNull(message = "批次ID不能为空")
    @TableField("batch_id")
    private Long batchId;

    /** Excel 行号（含表头，即数据首行为 2，与操作员在 Excel 里看到的一致） */
    @NotNull(message = "Excel 行号不能为空")
    @Min(value = 1, message = "Excel 行号必须为正")
    @TableField("row_no")
    private Integer rowNo;

    /** ISBN */
    @Size(max = 20, message = "ISBN 不能超过 20 个字符")
    @TableField("isbn")
    private String isbn;

    /** 书名 */
    @Size(max = 200, message = "书名不能超过 200 个字符")
    @TableField("title")
    private String title;

    /** 行状态（1可导入 2警告 3错误） */
    @Min(value = 1, message = "行状态取值 1（可导入）/ 2（警告）/ 3（错误）")
    @Max(value = 3, message = "行状态取值 1（可导入）/ 2（警告）/ 3（错误）")
    @TableField("row_status")
    private Integer rowStatus;

    /** 错误/警告原因（前端表格直接回显） */
    @Size(max = 500, message = "错误原因不能超过 500 个字符")
    @TableField("error_msg")
    private String errorMsg;

    /**
     * 原始行数据（解析时的整行快照，执行阶段据此落库）。
     *
     * <p>{@code @JsonInclude(NON_NULL)}：列表查询走的是不带 rawData 的 resultMap，
     * 该字段为 null —— 不加这个注解，Jackson 会给每一行都吐一个 {@code "rawData": null}，
     * 前端表格明明用不上还要接收。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @TableField(value = "raw_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rawData;
}
