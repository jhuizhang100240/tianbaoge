package com.tbgbook.bookstore.ai.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbgbook.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档（biz_kb_doc）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_kb_doc")
public class KbDoc extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @TableId(value = "doc_id", type = IdType.AUTO)
    private Long docId;

    private Long storeId;

    private String docName;

    /** 1规则制度 2服务规范 3会员权益 4图书元数据 5其他 */
    private Integer docType;

    private String fileUrl;

    /** pdf/docx/txt/md */
    private String fileType;

    private Long fileSize;

    private String version;

    private String effectiveScope;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer chunkTotal;

    private Integer chunkOk;

    private Integer chunkFailed;

    /** 1待处理 2处理中 3已完成 4失败 */
    private Integer indexStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime indexTime;

    private String failReason;

    /** 1启用 2停用 */
    private Integer status;

    @TableLogic
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String delFlag;
}
