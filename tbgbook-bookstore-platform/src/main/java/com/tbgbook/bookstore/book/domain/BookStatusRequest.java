package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 图书状态变更请求体（`POST /book/book/{bookId}/status`，FR-4.1.3）。
 *
 * <p><b>为什么 {@code status} 只有 {@code @NotNull}、没有 {@code @Min/@Max}</b>：
 * 契约给本端点的错误码表写的是「422 状态取值非法」。若在字段上标 {@code @Min(1)/@Max(4)}，
 * 越界会先被 {@code @Valid} 拦成 <b>400</b>（{@code MethodArgumentNotValidException}），
 * 与契约不符。故**范围校验放在 Service 里抛 422**，字段上只留「必填」这一条
 * —— 缺字段属「请求格式错误」，400 才是对的。
 *
 * @author tbgbook
 */
@Data
public class BookStatusRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书状态（1在售 2下架 3缺货 4停售） */
    @NotNull(message = "图书状态不能为空")
    private Integer status;

    /**
     * 变更原因。
     *
     * <p>⚠️ {@code biz_book_change_log} **没有原因列**（只有 field_name / before_value /
     * after_value），所以本字段不落变更日志表 —— 它由控制器上的 {@code @Log} 记入
     * {@code sys_oper_log.oper_param}（A-83 操作日志可查）。
     * 见 {@code BookController#changeStatus} 的说明。
     */
    @Size(max = 200, message = "变更原因不能超过 200 个字符")
    private String reason;
}
