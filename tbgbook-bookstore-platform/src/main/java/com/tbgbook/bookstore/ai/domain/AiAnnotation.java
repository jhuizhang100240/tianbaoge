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
 * AI 问答质量标注（biz_ai_annotation）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_ai_annotation")
public class AiAnnotation
{
    @TableId(value = "annotation_id", type = IdType.AUTO)
    private Long annotationId;

    private Long sessionId;

    private Long messageId;

    /** 1准确 2部分准确 3错误 */
    private Integer accuracy;

    /** 1知识缺失 2检索失败 3生成错误 4表述问题 */
    private Integer category;

    private String suggestion;

    private Long relatedDocId;

    private String annotateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime annotateTime;

    /** 1待标注 2已标注 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
