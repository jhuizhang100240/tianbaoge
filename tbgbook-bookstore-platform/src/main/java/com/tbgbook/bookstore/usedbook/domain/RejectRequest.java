package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 回收单整单拒收请求（`POST /usedbook/recycle/{recycleId}/reject`）。
 *
 * @author tbgbook
 */
@Data
public class RejectRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 拒收原因（必填） */
    private String rejectReason;
}
