package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 回收申请明细。
 */
@Data
public class RecycleApplyItem
{
    @NotBlank(message = "ISBN 不能为空")
    private String isbn;

    @NotNull(message = "成色不能为空")
    private Long submitGradeId;

    private String title;
}
