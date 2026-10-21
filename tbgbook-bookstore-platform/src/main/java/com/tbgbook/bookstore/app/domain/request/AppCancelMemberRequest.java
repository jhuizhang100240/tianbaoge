package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会员端注销请求。
 */
@Data
public class AppCancelMemberRequest
{
    @Size(max = 500, message = "注销原因不能超过 500 个字符")
    private String reason;

    @NotBlank(message = "确认文本不能为空")
    private String confirmText;
}
