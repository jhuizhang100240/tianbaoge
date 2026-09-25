package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import lombok.Data;

/** 订单导出请求（`POST /sales/order/export`）。筛选字段与 `GET /sales/order/list` 相同。 */
@Data
public class OrderExportRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否含成本字段（仅店长）；置 1 时导出任务标 `isSensitive=1` 需二次授权 */
    private Boolean costFields;

    /** 导出时间跨度起点（与列表同口径） */
    private String beginTime;

    /** 导出时间跨度终点 */
    private String endTime;
}
