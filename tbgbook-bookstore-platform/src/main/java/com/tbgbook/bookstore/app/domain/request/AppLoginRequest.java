package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信授权登录请求。
 */
@Data
public class AppLoginRequest
{
    @NotBlank(message = "微信登录凭证不能为空")
    private String code;

    private String encryptedData;

    private String iv;

    private String phoneCode;
}
