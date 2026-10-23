package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 手动触发预警扫描的请求体（`POST /inventory/warning/scan`）。
 *
 * <p>只有一个选填字段：不传即四类全扫。整个请求体也可省略。
 *
 * <p>本字段**不加范围校验注解**：契约把越界归到 **422 业务规则**
 * （「预警类型取值非法（1~4）」）而不是 400 参数格式，且要给出带语义的文案 ——
 * 由 {@code StockWarningServiceImpl#requireValidType} 判定。
 *
 * @author tbgbook
 */
@Data
public class WarningScanRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 只扫描指定类型（1补货 2滞销 3临期 4零库存），不传则四类全扫 */
    private Integer warningType;
}
