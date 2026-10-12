package com.tbgbook.bookstore.ai.index;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.common.exception.BizErrors;

/**
 * 解析器分派：按文件类型找第一个支持的实现；不支持时 422。
 *
 * @author tbgbook
 */
@Component
public class DocumentParserRegistry
{
    private final List<DocumentParser> parsers;

    public DocumentParserRegistry(List<DocumentParser> parsers)
    {
        this.parsers = parsers;
    }

    public String parse(String fileType, byte[] content)
    {
        return parsers.stream()
                .filter(parser -> parser.supports(fileType))
                .findFirst()
                .orElseThrow(() -> BizErrors.ruleViolated("不支持的文件类型：" + fileType))
                .parse(content);
    }
}
