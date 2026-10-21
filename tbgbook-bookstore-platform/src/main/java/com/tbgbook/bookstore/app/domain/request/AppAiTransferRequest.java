package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会员端转人工请求。
 */
@Data
public class AppAiTransferRequest
{
    @Size(max = 500, message = "转人工原因不能超过 500 个字符")
    private String reason;
}
