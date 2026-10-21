package com.tbgbook.bookstore.analysis.domain.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 经营数据问答请求。
 */
@Data
public class AnalysisAskRequest
{
    @NotBlank(message = "问题不能为空")
    private String question;

    private Long sessionId;

    private Boolean asChart;
}
