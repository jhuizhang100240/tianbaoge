package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 积分批次对象 biz_point_batch（§3.2 积分规则 3）。
 *
 * <p><b>为什么要按批次追踪</b>：积分有效期 24 个月，要准确实现「到期清零」与
 * 「先到期先消耗」就必须知道每一笔积分还剩多少、什么时候到期 —— 只记一个总余额
 * 回答不了这两个问题。
 *
 * <p>本表**只读接口对外暴露**（{@code GET /member/point-batch/*}）；
 * 批次由积分服务在变动时维护：新增走 {@code insert}，消耗按
 * {@code expire_time asc} 扣减 {@code remain_points}，扣到 0 置 {@code status=2 已用完}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_point_batch")
public class PointBatch implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 批次ID */
    @TableId(value = "batch_id", type = IdType.AUTO)
    private Long batchId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 来源（1消费获得 2人工调整 3活动赠送 4退货返还） */
    @TableField("source_type")
    private Integer sourceType;

    /** 批次总积分 */
    @TableField("total_points")
    private Integer totalPoints;

    /** 已使用积分 */
    @TableField("used_points")
    private Integer usedPoints;

    /** 剩余积分 */
    @TableField("remain_points")
    private Integer remainPoints;

    /** 获得时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("gain_time")
    private LocalDateTime gainTime;

    /** 过期时间（获得时间 + 有效期月数） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 状态（1有效 2已用完 3已过期清零） */
    @TableField("status")
    private Integer status;

    /** 关联单号 */
    @TableField("ref_no")
    private String refNo;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 请求参数（时间区间筛选用，与 {@code BaseEntity#params} 同口径；非表字段）。
     *
     * <p>列表筛选支持 {@code params[beginExpireTime] / params[endExpireTime] /
     * params[beginTime] / params[endTime]}，XML 直接引用 {@code params.*}，
     * 故实体必须提供该属性（本实体不继承 BaseEntity）。
     */
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
