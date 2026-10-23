package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 借阅续借记录对象 biz_borrow_renew（**只读资源**，FR-4.5.4）。
 *
 * <p>记续借前后应还日；续借动作本身走 {@code POST /borrow/record/{borrowId}/renew}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_borrow_renew")
public class BorrowRenew implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 续借渠道：1会员端自助 */
    public static final int CHANNEL_MEMBER = 1;

    /** 续借渠道：2店员代办 */
    public static final int CHANNEL_STAFF = 2;

    /** 续借ID */
    @TableId(value = "renew_id", type = IdType.AUTO)
    private Long renewId;

    /** 借阅单ID */
    @TableField("borrow_id")
    private Long borrowId;

    /** 续借前应还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("before_due_time")
    private LocalDateTime beforeDueTime;

    /** 续借后应还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("after_due_time")
    private LocalDateTime afterDueTime;

    /** 本次延长天数 */
    @TableField("renew_days")
    private Integer renewDays;

    /** 操作人（线上自助/店员代办） */
    @TableField("renew_by")
    private String renewBy;

    /** 续借渠道（1会员端自助 2店员代办） */
    @TableField("renew_channel")
    private Integer renewChannel;

    /** 续借时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

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
