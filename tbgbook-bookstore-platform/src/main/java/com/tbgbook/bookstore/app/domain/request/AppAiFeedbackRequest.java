package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员端 AI 回答反馈。
 */
@Data
public class AppAiFeedbackRequest
{
    @NotNull(message = "消息ID不能为空")
    private Long messageId;

    @NotNull(message = "反馈不能为空")
    @Min(value = 1, message = "反馈取值必须是 1 或 2")
    @Max(value = 2, message = "反馈取值必须是 1 或 2")
    private Integer feedback;
}
