package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会员端学号认证请求。
 */
@Data
public class StudentCertRequest
{
    @NotBlank(message = "学校不能为空")
    private String school;

    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private String major;

    private String grade;

    @NotBlank(message = "学生证照片不能为空")
    private String certImageUrl;
}
