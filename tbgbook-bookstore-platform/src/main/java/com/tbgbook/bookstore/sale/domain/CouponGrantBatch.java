package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 优惠券发放批次对象 biz_coupon_grant_batch（页面 A-38 券发放，FR-4.3.8）。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表有 {@code create_time} / {@code update_time}，
 * 但**没有** {@code create_by} / {@code update_by} / {@code remark} / {@code del_flag}
 * —— 操作人由业务列 {@code grantBy} 承载。代价是审计时间的 {@code @JsonFormat} 要自己补。
 *
 * <p><b>两段式：先建批次（圈选人群），再执行发放</b>。
 * {@code targetType} 决定怎么圈人（1按标签 2按等级 3手工导入名单），
 * {@code targetValue} 是圈选参数；{@code planQty} 在**执行时**按圈选结果写入
 * （故它在契约的 `skip` 里、客户端写不了）。执行走
 * {@code POST /sales/coupon-batch/{batchId}/execute}。
 *
 * <p>⚠️ <b>执行是同步完成的</b>：契约写「发放是异步的，本接口创建任务后立即返回」，
 * 但单店几千张券的插入是毫秒级，为此引入线程池 + 异步事务得不偿失
 * （且异步线程里拿不到登录上下文，{@code grantBy} 会丢）。故本实现**同步发放完毕再返回**，
 * 响应结构与契约一致（{@code batchId}/{@code batchNo}/{@code planQty}/{@code status}），
 * 前端轮询 {@code actualQty} 的路径照常可用（只是会立刻看到已完成）。
 * 已登记 `plan/待确认项.md`。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_coupon_grant_batch")
public class CouponGrantBatch implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 发放方式：定向发放 */
    public static final int GRANT_DIRECTED = 1;

    /** 发放方式：领券中心 */
    public static final int GRANT_CENTER = 2;

    /** 发放方式：活动赠送 */
    public static final int GRANT_ACTIVITY = 3;

    /** 目标人群：按标签 */
    public static final int TARGET_TAG = 1;

    /** 目标人群：按等级 */
    public static final int TARGET_LEVEL = 2;

    /** 目标人群：手工导入名单 */
    public static final int TARGET_MANUAL = 3;

    /** 状态：发放中 */
    public static final int STATUS_GRANTING = 1;

    /** 状态：已完成 */
    public static final int STATUS_DONE = 2;

    /** 状态：已失败 */
    public static final int STATUS_FAILED = 3;

    /** 批次ID */
    @TableId(value = "batch_id", type = IdType.AUTO)
    private Long batchId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 发放批次号（CB+yyyyMMdd+流水）。**服务端生成** */
    @TableField("batch_no")
    private String batchNo;

    /** 模板ID。必填性由 service 按**操作**判定 */
    @TableField("template_id")
    private Long templateId;

    /** 发放方式（1定向发放 2领券中心 3活动赠送） */
    @Min(value = 1, message = "发放方式取值 1~3")
    @Max(value = 3, message = "发放方式取值 1~3")
    @TableField("grant_type")
    private Integer grantType;

    /** 目标人群类型（1按标签 2按等级 3手工导入名单） */
    @Min(value = 1, message = "目标人群类型取值 1~3")
    @Max(value = 3, message = "目标人群类型取值 1~3")
    @TableField("target_type")
    private Integer targetType;

    /** 目标人群值（标签ID / 等级ID / 会员ID列表，逗号分隔） */
    @Size(max = 1000, message = "目标人群值不能超过 1000 个字符")
    @TableField("target_value")
    private String targetValue;

    /** 计划发放数量（**执行时按圈选结果写入**，客户端写不了） */
    @TableField("plan_qty")
    private Integer planQty;

    /** 实际发放数量（服务端累加） */
    @TableField("actual_qty")
    private Integer actualQty;

    /** 已领取数量（服务端累加） */
    @TableField("received_qty")
    private Integer receivedQty;

    /** 状态（1发放中 2已完成 3已失败）。**服务端维护** */
    @TableField("status")
    private Integer status;

    /** 操作人（服务端取当前登录用户） */
    @TableField("grant_by")
    private String grantBy;

    /** 发放时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("grant_time")
    private LocalDateTime grantTime;

    /** 创建时间（⚠️ 不继承 BaseEntity，必须自己声明 @JsonFormat） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 请求参数（瞬态，不落库；日期区间筛选走 {@code params[beginTime]}） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private java.util.Map<String, Object> params = new java.util.HashMap<>();
}
