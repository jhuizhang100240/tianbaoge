package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会员端 AI 问答请求。
 */
@Data
public class AppAiChatRequest
{
    @NotBlank(message = "问题内容不能为空")
    @Size(max = 1000, message = "问题内容不能超过 1000 个字符")
    private String question;

    private Long sessionId;

    @Size(max = 500, message = "入口上下文不能超过 500 个字符")
    private String entryContext;
}
