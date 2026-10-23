package com.tbgbook.bookstore.ai.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * AI 提示词模板版本（biz_ai_prompt_version）。
 *
 * @author tbgbook
 */
@Data
@TableName(value = "biz_ai_prompt_version", autoResultMap = true)
public class AiPromptVersion
{
    @TableId(value = "version_id", type = IdType.AUTO)
    private Long versionId;

    private Long templateId;

    private String versionNo;

    /** 模板内容（{{变量}} 占位符） */
    private String content;

    /** 声明的变量列表（MySQL json 列，接口契约类型 object；JacksonTypeHandler 双向转换） */
    @TableField(value = "variables", typeHandler = JacksonTypeHandler.class)
    private List<String> variables;

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
