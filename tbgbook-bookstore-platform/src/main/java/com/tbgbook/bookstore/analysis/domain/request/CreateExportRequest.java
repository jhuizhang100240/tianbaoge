package com.tbgbook.bookstore.analysis.domain.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发起异步导出请求。
 */
@Data
public class CreateExportRequest
{
    @NotBlank(message = "导出类型不能为空")
    private String exportType;

    private String taskName;

    private Map<String, Object> filters;

    private Boolean costFields;
}
