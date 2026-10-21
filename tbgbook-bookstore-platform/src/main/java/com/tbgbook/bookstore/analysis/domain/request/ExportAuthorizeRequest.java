package com.tbgbook.bookstore.analysis.domain.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 敏感导出二次授权请求。
 */
@Data
public class ExportAuthorizeRequest
{
    private Long taskId;

    @NotBlank(message = "授权事由不能为空")
    private String authReason;
}
