package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 会员消息对象 biz_message（页面 A-51，FR-4.4.10）。
 *
 * <p>{@code linkUrl} 为深链地址（到货→B-06、借阅到期→B-17、积分到期→B-23）。
 * 发送状态与失败原因由发送动作回写（1待发送 2已发送 3发送失败）；
 * **已发送（send_status=2）的消息不允许修改与删除（留痕）**（见 A40）。
 *
 * <p>本表无 {@code del_flag} 与 {@code update_*}：删除为物理删除。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_message")
public class MemberMessage implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 消息类型（1到货通知 2借阅到期 3催还 4积分到期 5活动通知 6系统消息 7人工回复） */
    @TableField("msg_type")
    private Integer msgType;

    /** 标题 */
    @TableField("title")
    private String title;

    /** 内容摘要 */
    @TableField("content")
    private String content;

    /** 深链地址 */
    @TableField("link_url")
    private String linkUrl;

    /** 关联业务单号 */
    @TableField("biz_no")
    private String bizNo;

    /** 发送渠道（1微信订阅消息 2站内信 3短信） */
    @TableField("send_channel")
    private Integer sendChannel;

    /** 发送状态（1待发送 2已发送 3发送失败） */
    @TableField("send_status")
    private Integer sendStatus;

    /** 发送失败原因（如未授权订阅） */
    @TableField("fail_reason")
    private String failReason;

    /** 发送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("send_time")
    private LocalDateTime sendTime;

    /** 是否已读（0否 1是） */
    @TableField("is_read")
    private Integer isRead;

    /** 阅读时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("read_time")
    private LocalDateTime readTime;

    /** 创建时间 */
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
