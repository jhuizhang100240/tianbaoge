package com.tbgbook.bookstore.ai.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * AI 调用明细（biz_ai_call_log，只增不删）。
 *
 * <p>scene：1客服 2推荐 3摘要 4经营问答 5选品 6知识库索引。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_ai_call_log")
public class AiCallLog
{
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private Long storeId;

    private Long sessionId;

    private Long memberId;

    private Integer scene;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer cacheHit;

    private BigDecimal cost;

    private Integer latencyMs;

    private Integer isSuccess;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
