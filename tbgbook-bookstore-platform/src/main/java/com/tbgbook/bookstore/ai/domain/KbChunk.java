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
 * 知识库切片（biz_kb_chunk）。
 *
 * <p>无 {@code del_flag}（派生数据，删除即物理删除）；无审计人字段，只有 create/update 时间
 * —— 故不继承 {@code BaseEntity}，时间字段由 {@code MyMetaObjectHandler} 填充。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_kb_chunk")
public class KbChunk
{
    @TableId(value = "chunk_id", type = IdType.AUTO)
    private Long chunkId;

    private Long docId;

    private Integer seqNo;

    private String content;

    private Integer charCount;

    /** Qdrant point UUID */
    private String vectorId;

    /** 0待处理 1成功 2失败 */
    private Integer vectorStatus;

    private String failReason;

    private Integer hitCount;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
