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
 * 会员冻结与黑名单对象 biz_member_freeze（FR-4.4.11，页面 A-52）。
 *
 * <p>生效中（{@code status=1}）的冻结/黑名单**同步把 {@code biz_member.status} 改为 2冻结 / 3黑名单**；
 * 解冻恢复为 1正常并保留记录（{@code status=2 已解除}）。历史记录不删除。
 *
 * <p>本表无 {@code del_flag}；但按「历史留痕」要求，删除端点恒 422（见 A39）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_member_freeze")
public class MemberFreeze implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(value = "freeze_id", type = IdType.AUTO)
    private Long freezeId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 类型（1冻结 2黑名单） */
    @TableField("freeze_type")
    private Integer freezeType;

    /** 原因（恶意逾期/异常退货等） */
    @TableField("reason")
    private String reason;

    /** 操作人（服务端写入） */
    @TableField("freeze_by")
    private String freezeBy;

    /** 冻结时间（服务端写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("freeze_time")
    private LocalDateTime freezeTime;

    /** 解除人（服务端写入） */
    @TableField("unfreeze_by")
    private String unfreezeBy;

    /** 解除时间（服务端写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("unfreeze_time")
    private LocalDateTime unfreezeTime;

    /** 解除原因（服务端写入） */
    @TableField("unfreeze_reason")
    private String unfreezeReason;

    /** 状态（1生效中 2已解除） */
    @TableField("status")
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
