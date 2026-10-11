package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 续借请求（`POST /borrow/record/{borrowId}/renew`，FR-4.5.4）。
 *
 * @author tbgbook
 */
@Data
public class RenewRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 续借渠道（1会员端自助 2店员代办），默认 2 */
    private Integer renewChannel;
}
