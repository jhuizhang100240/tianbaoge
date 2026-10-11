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
 * 会员等级变更记录对象 biz_member_level_log（FR-4.4.3，只读）。
 *
 * <p>升降级留痕，是「连续 12 个月成长值未达门槛才降级」缓冲机制的判定依据。
 * 本表**只增不改不删**：无 {@code del_flag}、无 {@code update_*}，只提供查询接口。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_member_level_log")
public class MemberLevelLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 变更前等级ID */
    @TableField("before_level_id")
    private Long beforeLevelId;

    /** 变更后等级ID */
    @TableField("after_level_id")
    private Long afterLevelId;

    /** 变更类型（1升级 2降级 3人工调整） */
    @TableField("change_type")
    private Integer changeType;

    /** 变更时成长值 */
    @TableField("growth_value")
    private Integer growthValue;

    /** 变更原因 */
    @TableField("change_reason")
    private String changeReason;

    /** 操作人 */
    @TableField("create_by")
    private String createBy;

    /** 变更时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
