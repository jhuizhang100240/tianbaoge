package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 借书办理请求（`POST /borrow/lend`，FR-4.5.2）。
 *
 * @author tbgbook
 */
@Data
public class LendRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 图书条码 / ISBN */
    private String code;

    /** 取书库位ID（可空，空则按可售库存最多的库位） */
    private Long locationId;

    /** 备注 */
    private String remark;
}
