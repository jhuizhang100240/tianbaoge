package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 会员标签关系对象 biz_member_tag_rel（FR-4.4.7）。
 *
 * <p>{@code uk_member_tag} 保证同一会员同一标签不重复；
 * {@code source} 1手动打标 2规则自动。**规则标签的关联不允许手工增删**。
 * 本表无 {@code del_flag}，解除打标即物理删行。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_member_tag_rel")
public class MemberTagRel implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 关系ID */
    @TableId(value = "rel_id", type = IdType.AUTO)
    private Long relId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 标签ID */
    @TableField("tag_id")
    private Long tagId;

    /** 来源（1手动打标 2规则自动；服务端接管，不接受直接写入） */
    @TableField("source")
    private Integer source;

    /** 操作人 */
    @TableField("create_by")
    private String createBy;

    /** 打标时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
