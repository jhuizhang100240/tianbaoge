package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 会员储值账户对象 biz_member_balance（FR-4.4.5，步骤②对外提供）。
 *
 * <p><b>本金 / 赠送金分列</b> —— 退款时可只退本金，财务能分出真实负债与营销费用。
 * 三个余额列与 {@code version}（乐观锁）由服务端维护，**充值 / 消费 / 退款一律走
 * 专用接口并写 {@code biz_stored_value_log}**，不接受直接写入。
 *
 * <p>步骤①只负责在会员建档时初始化一行空账户（{@code uk_member} 保证一会员一账户），
 * 读写接口随步骤②落地。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_member_balance")
public class MemberBalance implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 账户ID */
    @TableId(value = "balance_id", type = IdType.AUTO)
    private Long balanceId;

    /** 会员ID（唯一） */
    @TableField("member_id")
    private Long memberId;

    /** 本金余额 */
    @TableField("principal_balance")
    private BigDecimal principalBalance;

    /** 赠送金余额 */
    @TableField("gift_balance")
    private BigDecimal giftBalance;

    /** 余额合计（本金+赠送金） */
    @TableField("total_balance")
    private BigDecimal totalBalance;

    /** 乐观锁版本号 */
    @Version
    @TableField("version")
    private Integer version;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
