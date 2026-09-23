package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 预警扫描的结果（`POST /inventory/warning/scan` 的 {@code data}）。
 *
 * <p>契约要求 {@code scanned / created / updated} 三个数。它们的含义按
 * **扫描动作**而非**预警条数**定义，容易混：
 * <ul>
 *   <li>{@code scanned} —— 本次**扫描到的图书数**（全店未删除的图书数，
 *       不是「命中预警的书数」）。它反映扫描覆盖面，是排查「为什么没扫出来」的第一个数字</li>
 *   <li>{@code created} —— **新生成**的预警条数（该书该类型此前没有待处理预警）</li>
 *   <li>{@code updated} —— **就地更新**的预警条数（此前已有待处理预警，本次刷新了数值）</li>
 * </ul>
 * 故 {@code created + updated} 才是「本次命中预警的条数」，
 * 而它通常小于 {@code scanned}（多数书既不缺货也不滞销）。
 *
 * @author tbgbook
 */
@Data
public class WarningScanResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 扫描图书数（全店未删除图书） */
    private Integer scanned;

    /** 新生成预警数 */
    private Integer created;

    /** 就地更新数（已有待处理预警，刷新数值不新增行） */
    private Integer updated;
}
