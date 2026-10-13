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
 * AI 提示词模板版本（biz_ai_prompt_version）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_ai_prompt_version")
public class AiPromptVersion
{
    @TableId(value = "version_id", type = IdType.AUTO)
    private Long versionId;

    private Long templateId;

    private String versionNo;

    /** 模板内容（{{变量}} 占位符） */
    private String content;

    /** 声明的变量列表（JSON 数组文本） */
    @JsonRawValue
    private String variables;

    /** 1草稿 2灰度 3全量 4已回滚 */
    private Integer status;

    /** A/B 效果：该版本满意度 */
    private BigDecimal abSatisfaction;

    private String publishBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
