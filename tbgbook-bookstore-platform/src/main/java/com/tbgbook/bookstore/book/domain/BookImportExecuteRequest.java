package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 执行批量导入的请求体（`POST /book/import/{batchId}/execute`）
 *
 * <p>只有一个可选字段，但仍然建独立类而不用 {@code Map} —— 用 {@code Map} 接的话
 * 拼错字段名（{@code skipWarnRow}）会静默按默认值走，操作员以为跳过了警告行、
 * 实际全导进去了。
 *
 * @author tbgbook
 */
@Data
public class BookImportExecuteRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否跳过警告行，默认 false（警告行默认可导入） */
    private Boolean skipWarnRows;
}
