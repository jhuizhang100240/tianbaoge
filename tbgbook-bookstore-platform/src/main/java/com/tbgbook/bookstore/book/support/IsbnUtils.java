package com.tbgbook.bookstore.book.support;

/**
 * ISBN 归一化与格式校验。
 *
 * <p><b>为什么抽出来</b>：批量导入（{@code BookImportBatchServiceImpl}）与 ISBN 识别填充
 * （{@code IsbnLookupServiceImpl}）都要「去连字符 + 判 10/13 位」这一套。两处各写一份的风险是
 * **漂移** —— 一旦校验口径不一致，就会出现「识别填充能接受的 ISBN，批量导入判为非法」这种
 * 自相矛盾的行为。故收敛到这里，两边都调它。
 *
 * @author tbgbook
 */
public final class IsbnUtils
{
    /**
     * 需要剥掉的字符：空白 + 各类连字符。
     *
     * <p>不只 {@code -}：书封与出版社数据里常见 en dash / em dash / 非断行连字符，
     * 只处理 ASCII 连字符会把 {@code 978–7–115–42802–8} 判成非法（实测踩到）。
     */
    private static final String SEPARATORS = "[\\s\\-\\u2010\\u2011\\u2012\\u2013\\u2014]";

    /** 10 位 ISBN 的校验位允许 X（罗马数字 10） */
    private static final String ISBN_10 = "\\d{9}[\\dX]";

    /** 13 位 ISBN */
    private static final String ISBN_13 = "\\d{13}";

    private IsbnUtils()
    {
    }

    /**
     * 归一化 ISBN：去空白与连字符、转大写。
     *
     * @param isbn 原始值
     * @return 归一化结果；入参为空或只含分隔符时返回 {@code null}
     */
    public static String normalize(String isbn)
    {
        if (isbn == null)
        {
            return null;
        }
        String cleaned = isbn.replaceAll(SEPARATORS, "").toUpperCase();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 格式校验：**必须已归一化**（10 位数字且末位可 X，或 13 位数字）。
     *
     * <p>⚠️ 只校验**形状**，不校验校验位。这一点沿用导入侧的既有口径 ——
     * 数据库里存在历史遗留的错号图书，收紧成校验位会拒掉它们。
     *
     * @param isbn 已归一化的 ISBN
     * @return 是否合法；{@code null} 视为不合法
     */
    public static boolean isFormatOk(String isbn)
    {
        return isbn != null && (isbn.matches(ISBN_10) || isbn.matches(ISBN_13));
    }
}
