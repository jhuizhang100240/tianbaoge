package com.tbgbook.bookstore.book.domain.excel;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入模板的「字段说明」页（FastExcel 写模型）
 *
 * <p>原型 A-10 步骤①要求模板「含字段说明与示例行」。示例行由
 * {@link BookImportExcelRow} 写出，字段说明由本类写到第二个 sheet。
 *
 * @author tbgbook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookImportTemplateTip
{
    @ExcelProperty("列名")
    private String column;

    @ExcelProperty("是否必填")
    private String required;

    @ExcelProperty("说明")
    private String description;
}
