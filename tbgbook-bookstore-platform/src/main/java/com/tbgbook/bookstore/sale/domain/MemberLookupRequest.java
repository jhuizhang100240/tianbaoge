package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 收银台会员识别的请求体（`POST /sales/cashier/member-lookup`）。
 *
 * @author tbgbook
 */
@Data
public class MemberLookupRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 手机号 / 会员卡号（M+手机号）/ 会员码 */
    @NotBlank(message = "识别内容不能为空")
    private String keyword;
}
