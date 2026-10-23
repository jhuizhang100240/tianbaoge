package com.tbgbook.bookstore.book.domain.excel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 解析结果（`POST /book/import/parse` 的响应 `data`）
 *
 * <p>字段与接口文档「响应 data 结构」逐项对应：汇总计数 + 逐行结果数组。
 *
 * <p>逐行结果刻意**不复用 {@code BookImportRow} 实体** —— 那样会把
 * {@code rowId} / {@code batchId} / {@code rawData}（整行快照）一并吐给前端，
 * 而契约只声明 5 个字段。解析响应可能是几百行 × 每行一个完整快照，
 * 白白放大响应体。
 *
 * @author tbgbook
 */
@Data
public class BookImportParseResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 批次ID（后续 execute / error-file 都以此为准） */
    private Long batchId;

    /** 批次号 */
    private String batchNo;

    /** 总行数（不含表头） */
    private Integer totalRows;

    /** 可导入行数 */
    private Integer validRows;

    /** 警告行数 */
    private Integer warnRows;

    /** 错误行数 */
    private Integer errorRows;

    /** 逐行校验结果 */
    private List<Row> rows = new ArrayList<>();

    /**
     * 逐行校验结果（只含前端表格要展示的字段）。
     */
    @Data
    public static class Row implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** Excel 行号 */
        private Integer rowNo;

        private String isbn;

        private String title;

        /** 1可导入 2警告 3错误 */
        private Integer rowStatus;

        /** 错误/警告原因 */
        private String errorMsg;

        public Row(Integer rowNo, String isbn, String title, Integer rowStatus, String errorMsg)
        {
            this.rowNo = rowNo;
            this.isbn = isbn;
            this.title = title;
            this.rowStatus = rowStatus;
            this.errorMsg = errorMsg;
        }
    }
}
