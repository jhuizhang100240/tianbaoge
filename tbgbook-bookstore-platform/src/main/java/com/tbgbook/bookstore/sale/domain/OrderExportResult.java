package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 订单导出结果（`POST /sales/order/export`）。
 *
 * <p>只创建任务并返回 taskId（FR-4.6 规则 4：导出统一走异步任务 + 下载中心）；
 * 文件的**生成与下载属阶段八**（A-70 下载中心），本接口不做。
 */
@Data
public class OrderExportResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private String taskName;
    private Integer status;
}
