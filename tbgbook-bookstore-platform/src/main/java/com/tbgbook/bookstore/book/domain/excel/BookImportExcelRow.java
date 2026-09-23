package com.tbgbook.bookstore.book.domain.excel;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 图书批量导入的 Excel 行模型（FastExcel 读模型，页面 A-10 步骤②）
 *
 * <p><b>列与「下载导入模板」返回的文件严格一一对应</b>：模板由本类作为表头写出
 * （见 {@code BookImportBatchServiceImpl#buildTemplate}），解析时也由本类承接，
 * 两边共用同一份 {@code @ExcelProperty} 声明，避免模板与解析器漂移。
 *
 * <p><b>字段类型一律用 String</b>：Excel 里的单元格可能被填成数字、日期或文本，
 * 让 FastExcel 直接转成 {@code BigDecimal}/{@code Date} 会在格式不符时抛异常，
 * 整份文件解析中断。这里统一按字符串接住，再由
 * {@code BookImportBatchServiceImpl} 逐行做**可回显到单元格的**格式校验
 * （错误原因写进 {@code error_msg}，而不是让整批失败）。
 *
 * <p>只有前 3 列是必填（接口文档：表头缺少必填列 ISBN、书名、定价 → 422）；
 * 其余列缺失只影响该行的信息完整度，不阻断导入。
 *
 * @author tbgbook
 */
@Data
public class BookImportExcelRow
{
    /** 必填。13 位或 10 位，允许含连字符与空格 */
    @ExcelProperty("ISBN")
    private String isbn;

    /** 必填 */
    @ExcelProperty("书名")
    private String title;

    /** 必填。非负数 */
    @ExcelProperty("定价")
    private String price;

    @ExcelProperty("作者")
    private String author;

    /** 按名称匹配 biz_publisher；匹配不到则该行降级为警告并留空 */
    @ExcelProperty("出版社")
    private String publisherName;

    /** yyyy-MM-dd */
    @ExcelProperty("出版日期")
    private String publishDate;

    /** 按名称匹配 biz_category，且**只接受二级分类** */
    @ExcelProperty("分类")
    private String categoryName;

    /** 普通 / 教材，留空按「普通」处理 */
    @ExcelProperty("图书类型")
    private String bookType;

    @ExcelProperty("会员价")
    private String memberPrice;

    @ExcelProperty("版次")
    private String edition;

    @ExcelProperty("装帧")
    private String binding;

    @ExcelProperty("页数")
    private String pageCount;

    @ExcelProperty("内容简介")
    private String summary;

    // ------------------------------------------------------------------
    // 教材扩展（FR-4.1.8 / 原型 A-10 校验规则「教材扩展字段完整性」）
    // 仅当「图书类型 = 教材」时校验；三项齐全才顺带建 biz_book_textbook 扩展
    // ------------------------------------------------------------------

    /** 适用专业 */
    @ExcelProperty("适用专业")
    private String major;

    /** 适用学期（如 2026 秋季） */
    @ExcelProperty("适用学期")
    private String term;

    /** 开课院系 */
    @ExcelProperty("开课院系")
    private String college;
}
