package com.tbgbook.bookstore.ai.index;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.exception.ServiceException;

/**
 * PDF 文本抽取（PDFBox 3.x：{@code Loader.loadPDF}；2.x 的 {@code PDDocument.load} 已移除）。
 *
 * @author tbgbook
 */
@Component
public class PdfDocumentParser implements DocumentParser
{
    @Override
    public boolean supports(String fileType)
    {
        return "pdf".equalsIgnoreCase(fileType);
    }

    @Override
    public String parse(byte[] content)
    {
        try (PDDocument document = Loader.loadPDF(content))
        {
            if (document.isEncrypted())
            {
                throw BizErrors.ruleViolated("PDF 已加密，无法解析");
            }
            return new PDFTextStripper().getText(document);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException | RuntimeException e)
        {
            throw BizErrors.ruleViolated("PDF 解析失败：" + e.getMessage());
        }
    }
}
