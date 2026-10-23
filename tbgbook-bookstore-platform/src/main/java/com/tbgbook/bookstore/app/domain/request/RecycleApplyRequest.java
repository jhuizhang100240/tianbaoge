package com.tbgbook.bookstore.app.domain.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员端回收申请。
 */
@Data
public class RecycleApplyRequest
{
    @NotEmpty(message = "回收图书不能为空")
    @Valid
    private List<RecycleApplyItem> items;

    @NotNull(message = "预约日期不能为空")
    private LocalDate appointmentDate;

    private String appointmentSlot;

    private String remark;
}
