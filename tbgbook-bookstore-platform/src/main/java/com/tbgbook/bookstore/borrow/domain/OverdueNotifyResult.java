package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 催还提醒结果（`POST /borrow/overdue/notify` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class OverdueNotifyResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 成功推送数 */
    private Integer sent;

    /** 失败数 */
    private Integer failed;

    /** 失败明细 */
    private List<FailDetail> failDetails = new ArrayList<>();

    /** 单条失败原因 */
    @Data
    public static class FailDetail implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 借阅单ID */
        private Long borrowId;

        /** 失败原因 */
        private String reason;
    }
}
