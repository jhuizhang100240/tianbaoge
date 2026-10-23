package com.tbgbook.bookstore.ai.index;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.common.exception.BizErrors;

/**
 * 纯文本 / Markdown 解析：UTF-8 严格解码 → 失败回退 GBK（中文书店环境常见）→ 都失败 422。
 *
 * @author tbgbook
 */
@Component
public class TextDocumentParser implements DocumentParser
{
    @Override
    public boolean supports(String fileType)
    {
        return "txt".equalsIgnoreCase(fileType) || "md".equalsIgnoreCase(fileType);
    }

    @Override
    public String parse(byte[] content)
    {
        try
        {
            return decode(content, "UTF-8");
        }
        catch (CharacterCodingException utf8Failed)
        {
            try
            {
                return decode(content, "GBK");
            }
            catch (CharacterCodingException gbkFailed)
            {
                throw BizErrors.ruleViolated("文本编码无法识别（支持 UTF-8 / GBK）");
            }
        }
    }

    private String decode(byte[] content, String charset) throws CharacterCodingException
    {
        CharsetDecoder decoder = java.nio.charset.Charset.forName(charset).newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(content)).toString();
    }
}
