package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 逾期清单与汇总（`GET /borrow/record/overdue` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class OverdueResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 逾期明细 */
    private List<OverdueItem> list = new ArrayList<>();

    /** 汇总 */
    private Summary summary;

    /** 汇总统计 */
    @Data
    public static class Summary implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 逾期人数（去重） */
        private Integer memberCount;

        /** 逾期册数 */
        private Integer borrowCount;

        /** 罚金合计 */
        private BigDecimal fineTotal;
    }
}
