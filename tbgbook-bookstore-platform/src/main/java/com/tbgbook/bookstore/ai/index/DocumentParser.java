package com.tbgbook.bookstore.ai.index;

/**
 * 文档解析器（按文件类型分派）。
 *
 * <p>实现只做「字节 → 纯文本」一件事：不切片、不碰数据库、不调远端。
 *
 * @author tbgbook
 */
public interface DocumentParser
{
    /**
     * 是否支持该文件类型（小写扩展名，pdf/docx/txt/md）。
     */
    boolean supports(String fileType);

    /**
     * 解析为纯文本；无法解析时抛 422（BizErrors）。
     */
    String parse(byte[] content);
}
