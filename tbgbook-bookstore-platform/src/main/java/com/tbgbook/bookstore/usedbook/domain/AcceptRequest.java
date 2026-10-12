package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 回收单验收请求（`POST /usedbook/recycle/{recycleId}/accept`，FR-4.5.7 / 流程二）。
 *
 * @author tbgbook
 */
@Data
public class AcceptRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 验收明细数组 */
    private List<Item> items = new ArrayList<>();

    /** 结算方式（1现金 2储值账户 3积分） */
    private Integer settleType;

    /** 单行验收判定 */
    @Data
    public static class Item implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 回收明细ID */
        private Long itemId;

        /** 店员判定的成色等级ID */
        private Long judgeGradeId;

        /** 成色判定说明（与会员提交不一致时必填） */
        private String judgeNote;

        /** 状态（2合格已收 3拒收） */
        private Integer itemStatus;

        /** 拒收原因（itemStatus=3 时必填） */
        private String rejectReason;
    }
}
