package com.tbgbook.bookstore.ai.index;

import java.io.ByteArrayInputStream;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.exception.ServiceException;

/**
 * DOCX 文本抽取（POI XWPF）。
 *
 * @author tbgbook
 */
@Component
public class DocxDocumentParser implements DocumentParser
{
    @Override
    public boolean supports(String fileType)
    {
        return "docx".equalsIgnoreCase(fileType);
    }

    @Override
    public String parse(byte[] content)
    {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document))
        {
            return extractor.getText();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw BizErrors.ruleViolated("DOCX 解析失败：" + e.getMessage());
        }
    }
}
