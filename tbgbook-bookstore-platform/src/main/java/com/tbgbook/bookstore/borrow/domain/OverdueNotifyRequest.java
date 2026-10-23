package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 催还提醒请求（`POST /borrow/overdue/notify`）。
 *
 * @author tbgbook
 */
@Data
public class OverdueNotifyRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 借阅单ID数组 */
    private List<Long> borrowIds;
}
