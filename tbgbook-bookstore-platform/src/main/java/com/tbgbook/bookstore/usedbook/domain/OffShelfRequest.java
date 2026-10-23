package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 二手书下架请求（`POST /usedbook/stock/{usedBookId}/off-shelf`）。
 *
 * @author tbgbook
 */
@Data
public class OffShelfRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 下架原因（必填） */
    private String reason;
}
