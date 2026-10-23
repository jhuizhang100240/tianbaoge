package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会员端订阅消息授权上报。
 */
@Data
public class AppSubscribeAuthRequest
{
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    @Min(value = 1, message = "授权次数至少为 1")
    private Integer authTimes = 1;
}
