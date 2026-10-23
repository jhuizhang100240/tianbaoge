package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 还书办理请求（`POST /borrow/return`，FR-4.5.3）。
 *
 * @author tbgbook
 */
@Data
public class ReturnRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书条码（正价书用 ISBN 也可） */
    private String code;

    /** 图书状况（1完好 2轻微破损 3严重破损），默认 1 */
    private Integer bookCondition;

    /** 是否转赔偿流程，默认 false */
    private Boolean compensate;

    /** 备注 */
    private String remark;
}
