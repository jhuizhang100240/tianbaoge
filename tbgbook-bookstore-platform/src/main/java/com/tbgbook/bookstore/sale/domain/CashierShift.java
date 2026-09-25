package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 收银员班次对象 biz_cashier_shift（A-28 收银台，FR-4.3.12）。
 *
 * <p><b>未关联班次的用户即使有收银菜单权限也无法开班收银</b>
 * （{@code biz_cashier_shift} 与 {@code sys_user} 关联，契约原文）。
 *
 * <p><b>位置上的一个例外</b>：本对象的 URL 前缀是 {@code /system/shift}（平台侧），
 * 按《技术选型方案》的模块职责表它理应落在 {@code tbgbook-system}；但它的业务语义（收银班次、
 * 交班现金差异、与日结对账的班次对应）全在销售域，且增设依赖要动 pom。
 * **本次自定：类放销售域、URL 用 {@code /system/shift}**，与契约一致。已登记 A31。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time} / {@code update_time}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_cashier_shift")
public class CashierShift implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 状态：进行中 */
    public static final int STATUS_OPEN = 1;

    /** 状态：已交班 */
    public static final int STATUS_CLOSED = 2;

    @TableId(value = "shift_id", type = IdType.AUTO)
    private Long shiftId;

    @TableField("store_id")
    private Long storeId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("shift_date")
    private LocalDate shiftDate;

    /** 班次编码（如 MORNING 早班） */
    @TableField("shift_code")
    private String shiftCode;

    /** 班次名称（如 早班 08:00-15:00）。日结的 {@code shift} 列存的就是它 */
    @TableField("shift_name")
    private String shiftName;

    @TableField("user_id")
    private Long userId;

    /** 收银员账号（冗余） */
    @TableField("user_name")
    private String userName;

    /** 收银员姓名（冗余） */
    @TableField("nick_name")
    private String nickName;

    /** 开班备用金（现金找零备用） */
    @TableField("open_amount")
    private BigDecimal openAmount;

    /** 交班现金实点金额 */
    @TableField("close_amount")
    private BigDecimal closeAmount;

    /** 本班次订单笔数（**服务端算**：交班时按窗口聚合回填） */
    @TableField("order_count")
    private Integer orderCount;

    /** 本班次收款总额（**服务端算**） */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 关联日结单ID（0 = 尚未日结） */
    @TableField("settle_id")
    private Long settleId;

    /** 状态（1进行中 2已交班） */
    @TableField("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("open_time")
    private LocalDateTime openTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("close_time")
    private LocalDateTime closeTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 请求参数（瞬态，不落库；日期区间筛选走 {@code params[beginTime]}） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
