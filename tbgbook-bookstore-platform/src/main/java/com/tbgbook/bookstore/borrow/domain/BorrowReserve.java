package com.tbgbook.bookstore.borrow.domain;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tbgbook.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 借阅预约对象 biz_borrow_reserve（页面 A-58、B-18，FR-4.5.6）。
 *
 * <p>对已借出图书发起预约，归还后**按队列序号先后通知**；
 * {@code queueNo} 服务端分配（同一图书下自增）；
 * {@code expireTime = 到书通知时间 + biz_borrow_rule.reserve_hold_days}，
 * 超期未取置 {@code status=5 已过期}，队列顺延。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_borrow_reserve")
public class BorrowReserve extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：1排队中 */
    public static final int STATUS_QUEUING = 1;

    /** 状态：2已到书待取 */
    public static final int STATUS_NOTIFIED = 2;

    /** 状态：3已取书 */
    public static final int STATUS_PICKED = 3;

    /** 状态：4已取消 */
    public static final int STATUS_CANCELLED = 4;

    /** 状态：5已过期 */
    public static final int STATUS_EXPIRED = 5;

    /** 预约ID */
    @TableId(value = "reserve_id", type = IdType.AUTO)
    private Long reserveId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 预约单号（RS+yyyyMMddHHmmss+4 位流水） */
    @TableField("reserve_no")
    private String reserveNo;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** ISBN（冗余） */
    @TableField("isbn")
    private String isbn;

    /** 书名（冗余） */
    @TableField("title")
    private String title;

    /** 队列序号（同一图书下自增） */
    @TableField("queue_no")
    private Integer queueNo;

    /** 状态（1排队中 2已到书待取 3已取书 4已取消 5已过期） */
    @TableField("status")
    private Integer status;

    /** 预约时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("reserve_time")
    private LocalDateTime reserveTime;

    /** 到书通知时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("notify_time")
    private LocalDateTime notifyTime;

    /** 取书截止时间（到馆后保留 N 天） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 取书时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("pickup_time")
    private LocalDateTime pickupTime;

    /** 查询用：创建时间区间（契约 {@code params[beginTime]} / {@code params[endTime]}，不落库） */
    @JsonIgnore
    @TableField(exist = false)
    private Map<String, Object> params;

    /**
     * 通用查询参数（首次访问时初始化）。
     *
     * @return 参数映射（beginTime / endTime 等）
     */
    public Map<String, Object> getParams()
    {
        if (params == null)
        {
            params = new HashMap<>();
        }
        return params;
    }

    public void setParams(Map<String, Object> params)
    {
        this.params = params;
    }
}
