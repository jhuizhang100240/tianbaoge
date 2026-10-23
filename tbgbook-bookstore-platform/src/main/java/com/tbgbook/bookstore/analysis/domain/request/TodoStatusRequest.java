package com.tbgbook.bookstore.analysis.domain.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 待办状态流转请求。
 */
@Data
public class TodoStatusRequest
{
    @NotNull(message = "待办ID不能为空")
    private Long todoId;

    @NotNull(message = "状态不能为空")
    private Integer status;

    private String remark;
}
