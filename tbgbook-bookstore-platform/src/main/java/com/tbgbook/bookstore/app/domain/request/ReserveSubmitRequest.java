package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员端教材预订请求。
 */
@Data
public class ReserveSubmitRequest
{
    @NotNull(message = "教材清单明细不能为空")
    private Long listItemId;

    @NotNull(message = "预订数量不能为空")
    @Min(value = 1, message = "预订数量至少为 1")
    private Integer qty;

    private String contactName;

    private String contactPhone;
}
