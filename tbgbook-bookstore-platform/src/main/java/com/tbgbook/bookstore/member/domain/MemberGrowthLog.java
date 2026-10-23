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
 * 成长值流水对象 biz_member_growth_log（§4.4 规则 2，只增不减）。
 *
 * <p>来源：1消费（1 元 = 1 成长值）2按时归还（+5/次）3评价（+2/次）4人工调整。
 * 积分不参与成长值计算（§3.2 积分规则 1）。只增不删：无 {@code del_flag} 与 {@code update_*}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_member_growth_log")
public class MemberGrowthLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 变更类型（1消费 2按时归还 3评价 4人工调整） */
    @TableField("change_type")
    private Integer changeType;

    /** 成长值变动（正数，只增不减） */
    @TableField("growth_change")
    private Integer growthChange;

    /** 变动后成长值 */
    @TableField("growth_after")
    private Integer growthAfter;

    /** 关联单号（订单号/借阅单号） */
    @TableField("ref_no")
    private String refNo;

    /** 操作人 */
    @TableField("create_by")
    private String createBy;

    /** 发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
