package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.tbgbook.common.annotation.Sensitive;
import com.tbgbook.common.enums.DesensitizedType;

import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 会员教育信息与学号认证对象 biz_member_student（FR-4.4.1，1:1 挂在会员上）。
 *
 * <p><b>学号认证为可选操作</b>，认证成功后享受学生专享活动（§4.4 规则 1、C10）。
 * {@code certType} 1校方接口 2人工审核（默认）；提交走
 * {@code POST /member/member/student/submit}（置 1 审核中），审核走
 * {@code POST /member/member/student/{memberId}/audit}。
 *
 * <p><b>脱敏（NFR-S8）</b>：学号序列化时脱敏为「前 4 位 + ****」
 * （{@link Sensitive} + {@link DesensitizedType#STUDENT_NO}；管理员明文豁免沿用 A34 口径）。
 *
 * <p>主键即 {@code member_id}（1:1），删除为物理删除；本表无 {@code del_flag}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_member_student")
public class MemberStudent implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID（主键即外键，1:1） */
    @TableId(value = "member_id", type = IdType.INPUT)
    private Long memberId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 学校 */
    @Size(max = 100, message = "学校不能超过 100 个字符")
    @TableField("school")
    private String school;

    /** 学号（敏感：前 4 位 + **** 脱敏返回） */
    @Size(max = 50, message = "学号不能超过 50 个字符")
    @Sensitive(desensitizedType = DesensitizedType.STUDENT_NO)
    @TableField("student_no")
    private String studentNo;

    /** 姓名 */
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    @TableField("real_name")
    private String realName;

    /** 专业 */
    @Size(max = 100, message = "专业不能超过 100 个字符")
    @TableField("major")
    private String major;

    /** 年级 */
    @Size(max = 20, message = "年级不能超过 20 个字符")
    @TableField("grade")
    private String grade;

    /** 认证状态（0未认证 1审核中 2已认证 3已拒绝） */
    @TableField("cert_status")
    private Integer certStatus;

    /** 认证方式（1校方接口 2人工审核） */
    @TableField("cert_type")
    private Integer certType;

    /** 学生证照片 */
    @Size(max = 500, message = "学生证照片地址不能超过 500 个字符")
    @TableField("cert_image_url")
    private String certImageUrl;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("apply_time")
    private LocalDateTime applyTime;

    /** 审核人 */
    @TableField("audit_by")
    private String auditBy;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /** 拒绝原因 */
    @Size(max = 200, message = "拒绝原因不能超过 200 个字符")
    @TableField("reject_reason")
    private String rejectReason;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
