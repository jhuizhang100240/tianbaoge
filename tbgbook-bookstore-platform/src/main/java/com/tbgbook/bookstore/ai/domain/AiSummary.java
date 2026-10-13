package com.tbgbook.bookstore.ai.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 图书摘要与书评（biz_ai_summary；未审核禁止发布到会员端）。
 *
 * @author tbgbook
 */
@Data
@TableName(value = "biz_ai_summary", autoResultMap = true)
public class AiSummary
{
    @TableId(value = "summary_id", type = IdType.AUTO)
    private Long summaryId;

    private Long storeId;

    private Long bookId;

    private String isbn;

    private String title;

    /** 1内容简介 2适读人群 3卖点标签 4书评 */
    private Integer genType;

    private String content;

    /** 1AI生成 2出版社原始简介 */
    private Integer sourceType;

    /** 引用来源（知识库片段） */
    @TableField(value = "citations", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> citations;

    /** 1待审核 2已通过 3已驳回 4已发布 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime genTime;

    private String auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    private String rejectReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
