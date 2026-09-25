package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 促销活动启停的结果（`POST /sales/promotion/{promotionId}/status` 的 {@code data}）。
 *
 * <p>契约要求「启用前校验时间区间不与其他进行中活动冲突并**给出告警**」——
 * 是**告警**不是**拒绝**：同一商品命中多个活动时按 {@code priority} 取一、不相加
 * （契约业务约束），所以时间重叠**不构成错误**，只是提醒运营「这两个活动会互相盖住」。
 * 故冲突写进 {@link #warnings} 而**不影响状态变更成功**。
 *
 * <p>真正会拦下来的只有两件事（契约错误码）：状态流转非法、**活动商品为空**
 * （一个没有商品的活动启用了也不会有任何效果）。
 *
 * @author tbgbook
 */
@Data
public class PromotionStatusResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 活动ID */
    private Long promotionId;

    /** 活动编号 */
    private String promotionNo;

    /** 变更后的状态 */
    private Integer status;

    /**
     * 告警列表（**非阻断**）。当前包含两类：
     * <ul>
     *   <li>与其它**进行中**活动的时间区间重叠 —— 提示「同一商品会按优先级取一」</li>
     *   <li>启用时活动已过 {@code endTime} —— 提示「启用了也不会生效」</li>
     * </ul>
     * 无告警时为空数组（而不是 null，前端不必判空）。
     */
    private List<String> warnings;
}
