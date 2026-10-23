package com.tbgbook.bookstore.ai.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * AI 会话（biz_ai_session；无 del_flag / update_*，删除为物理删除并级联消息与标注）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_ai_session")
public class AiSession
{
    @TableId(value = "session_id", type = IdType.AUTO)
    private Long sessionId;

    private Long storeId;

    private String sessionNo;

    private Long memberId;

    private String memberName;

    /** 1智能客服 2图书推荐 3摘要生成 4经营问答 5选品建议 */
    private Integer scene;

    /** 1会员端小程序 2管理后台 */
    private Integer source;

    private String entryContext;

    private Integer turnCount;

    private Integer hasTransfer;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transferTime;

    private String transferReason;

    private String agentBy;

    private Integer satisfaction;

    private Integer isOfflineMsg;

    /** 1进行中 2已结束 3已转人工 4待接入 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
