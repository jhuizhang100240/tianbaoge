package com.tbgbook.bookstore.member.domain;

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
 * 订阅消息授权记录对象 biz_subscribe_auth（**只读资源**，页面 A-51）。
 *
 * <p>微信订阅消息为一次性订阅：{@code authTimes} 记录本次授权可发送次数（通常为 1），
 * {@code usedTimes} 为已用次数，两者相等时置 {@code authStatus=3 已用完}。
 * 授权由小程序端回写；**发送动作会扣减剩余次数**（副作用在 message/send 内）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_subscribe_auth")
public class SubscribeAuth implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 授权ID */
    @TableId(value = "auth_id", type = IdType.AUTO)
    private Long authId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 模板编码 */
    @TableField("template_code")
    private String templateCode;

    /** 授权状态（1已授权 2未授权 3已用完） */
    @TableField("auth_status")
    private Integer authStatus;

    /** 本次授权可发送次数（微信一次性订阅=1） */
    @TableField("auth_times")
    private Integer authTimes;

    /** 已使用次数 */
    @TableField("used_times")
    private Integer usedTimes;

    /** 授权时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("auth_time")
    private LocalDateTime authTime;

    /** 授权过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;

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
