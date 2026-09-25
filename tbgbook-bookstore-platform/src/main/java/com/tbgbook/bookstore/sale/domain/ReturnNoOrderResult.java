package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import lombok.Data;

/** 无单退货结果：returnId / returnNo / status（恒为 1待审核）。 */
@Data
public class ReturnNoOrderResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long returnId;
    private String returnNo;
    private Integer status;
}
