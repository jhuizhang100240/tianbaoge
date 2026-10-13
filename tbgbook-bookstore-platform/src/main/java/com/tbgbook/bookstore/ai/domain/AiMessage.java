package com.tbgbook.bookstore.ai.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonRawValue;

import lombok.Data;

/**
 * AI 会话消息（biz_ai_message）。
 *
 * <p>{@code citations} / {@code realtimeData} 为 JSON 文本，序列化时按 raw 值输出为对象
 * （写入侧保证只存合法 JSON 或 null，见后续问答内核）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_ai_message")
public class AiMessage
{
    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;

    private Long sessionId;

    /** 1会员 2AI 3人工客服 */
    private Integer role;

    private String content;

    private BigDecimal confidence;

    private Integer isLowConfidence;

    private Integer isFallback;

    @JsonRawValue
    private String citations;

    @JsonRawValue
    private String realtimeData;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private Integer firstTokenMs;

    /** 0未反馈 1满意 2不满意 */
    private Integer feedback;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
