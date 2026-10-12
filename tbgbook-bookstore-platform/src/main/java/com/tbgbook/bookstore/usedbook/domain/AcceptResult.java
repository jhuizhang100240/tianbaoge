package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 回收单验收结果（`POST /usedbook/recycle/{recycleId}/accept` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class AcceptResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 最终回收价合计 */
    private BigDecimal finalAmount;

    /** 是否存在成色判定不一致 */
    private Integer hasMismatch;

    /** 不一致明细（并排返回提交档与判定档，供页面高亮对比） */
    private List<MismatchItem> mismatchItems = new ArrayList<>();

    /** 生成的二手书副本ID */
    private List<Long> generatedUsedBookIds = new ArrayList<>();

    /** 不一致明细行 */
    @Data
    public static class MismatchItem implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 明细ID */
        private Long itemId;

        /** ISBN */
        private String isbn;

        /** 书名 */
        private String title;

        /** 会员提交成色 */
        private String submitGradeName;

        /** 店员判定成色 */
        private String judgeGradeName;

        /** 判定说明 */
        private String judgeNote;
    }
}
