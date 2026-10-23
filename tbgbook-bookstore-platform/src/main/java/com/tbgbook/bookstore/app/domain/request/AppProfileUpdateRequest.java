package com.tbgbook.bookstore.app.domain.request;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会员端资料修改请求。
 */
@Data
public class AppProfileUpdateRequest
{
    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    @Size(max = 500, message = "头像地址不能超过 500 个字符")
    private String avatar;

    @Min(value = 0, message = "性别取值必须是 0/1/2")
    @Max(value = 2, message = "性别取值必须是 0/1/2")
    private Integer gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;
}
