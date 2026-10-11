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
 * 积分流水对象 biz_point_log（只增不删）。
 *
 * <p>变动类型：1消费获得 2抵扣消耗 3到期清零 4人工调整 5退货返还。
 * {@code batchId} 在消耗时定位被扣减的具体批次（先到期先消耗）；一次消耗跨多个批次时
 * **每个批次一条流水**，保证任一批次的扣减都可追溯。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_point_log")
public class PointLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 变动类型（1消费获得 2抵扣消耗 3到期清零 4人工调整 5退货返还） */
    @TableField("change_type")
    private Integer changeType;

    /** 积分变动（正增负减） */
    @TableField("points_change")
    private Integer pointsChange;

    /** 变动后积分余额 */
    @TableField("points_after")
    private Integer pointsAfter;

    /** 关联积分批次ID（消耗时定位批次；0=未关联批次） */
    @TableField("batch_id")
    private Long batchId;

    /** 关联单号 */
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
