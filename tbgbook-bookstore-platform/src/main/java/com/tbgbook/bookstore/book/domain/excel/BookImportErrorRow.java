package com.tbgbook.bookstore.book.domain.excel;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入失败明细行（FastExcel 写模型，`GET /book/import/{batchId}/error-file`）
 *
 * <p>列刻意保持精简：操作员拿到后**只需对照 Excel 行号回原文件改**，
 * 所以带上 ISBN 与书名帮助定位，再加一列失败原因即可 —— 把整行 16 列都写回去
 * 反而会让「哪一列错了」淹没在数据里。
 *
 * @author tbgbook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookImportErrorRow
{
    /** 与原文件一致的行号，便于对照修改 */
    @ExcelProperty("Excel 行号")
    private Integer rowNo;

    @ExcelProperty("ISBN")
    private String isbn;

    @ExcelProperty("书名")
    private String title;

    /** 失败原因（跳过类的原因也会写在这里） */
    @ExcelProperty("失败原因")
    private String errorMsg;
}
