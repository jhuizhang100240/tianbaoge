package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 即将到期积分条目（`GET /member/point/expiring` 的 list 元素）。
 *
 * @author tbgbook
 */
@Data
public class PointExpiringItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 会员卡号 */
    private String memberNo;

    /** 昵称 */
    private String nickname;

    /** 该批次剩余积分 */
    private Integer remainPoints;

    /** 过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 距过期剩余天数（按应用侧时间计算，服务端补出） */
    private Long daysLeft;
}
