package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 收银挂单对象 biz_order_hold（页面 A-28 收银台挂单，FR-4.3.2）。
 *
 * <p><b>挂单是 T+0 临时态</b>：{@code cartData} 以 JSON 存购物车快照，
 * 故**不支持按商品检索挂单**（契约明写）—— 挂单列表只能按单号/金额/时间/会员筛。
 *
 * <p><b>挂单不锁库存</b>（契约明写）：挂单期间商品可能被他人买走，
 * 所以取单（{@code POST /{holdId}/resume}）时**必须重新校验库存与价格**，
 * 变动的部分在响应里以 {@code changes} 数组标出，由收银员二次确认。
 * 这条是本资源与「下单锁库存」最大的区别。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表有 {@code create_time} / {@code update_time}，
 * 但**没有** {@code create_by} / {@code update_by} / {@code remark} / {@code del_flag}
 * —— 操作人由 {@code holdBy} 承载。代价是审计时间的 {@code @JsonFormat} 要自己补。
 *
 * <p><b>{@code cartData} 是 MySQL {@code json} 列</b>，接口文档声明其类型为 {@code object}，
 * 因此用 MP 的 {@link JacksonTypeHandler} 做 Map ↔ json 双向转换。
 * ⚠️ 必须同时标 {@code autoResultMap = true}，否则 MP 生成的默认 resultMap 不会带上
 * typeHandler，读出来是字符串而不是对象（与 {@code BookImportRow#rawData} 同一处置）。
 *
 * @author tbgbook
 */
@Data
@TableName(value = "biz_order_hold", autoResultMap = true)
public class OrderHold implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 状态：挂单中 */
    public static final int STATUS_HOLDING = 1;

    /** 状态：已取单结算 */
    public static final int STATUS_RESUMED = 2;

    /** 状态：已作废 */
    public static final int STATUS_VOIDED = 3;

    /** 挂单ID */
    @TableId(value = "hold_id", type = IdType.AUTO)
    private Long holdId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 挂单号（GH+yyyyMMdd+流水）。**服务端生成** */
    @TableField("hold_no")
    private String holdNo;

    /** 会员ID（0=散客） */
    @TableField("member_id")
    private Long memberId;

    /** 会员昵称（冗余） */
    @TableField("member_name")
    private String memberName;

    /** 商品件数（服务端按 cartData 汇总） */
    @TableField("total_qty")
    private Integer totalQty;

    /** 挂单金额（服务端按 cartData 汇总） */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 购物车快照（json 列，原样保存供取单还原）。
     *
     * <p>{@code @JsonInclude(NON_NULL)}：**列表查询走的是不带 cartData 的 resultMap**
     * （见 `OrderHoldMapper.xml` 的说明：列表按单号/金额/时间/会员筛，不需要购物车内容），
     * 该字段为 null —— 不加这个注解，Jackson 会给每一行都吐一个 {@code "cartData": null}。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @TableField(value = "cart_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> cartData;

    /** 状态（1挂单中 2已取单结算 3已作废）。**服务端维护**：取单与作废各有动作端点 */
    @TableField("status")
    private Integer status;

    /** 挂单人（服务端取当前登录用户） */
    @TableField("hold_by")
    private String holdBy;

    /** 挂单时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("hold_time")
    private LocalDateTime holdTime;

    /** 取单时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("resume_time")
    private LocalDateTime resumeTime;

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
    private Map<String, Object> params = new HashMap<>();
}
