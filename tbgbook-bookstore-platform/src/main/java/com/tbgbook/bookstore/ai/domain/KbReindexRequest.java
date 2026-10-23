package com.tbgbook.bookstore.ai.domain;

import lombok.Data;

/**
 * 重建索引请求（{@code POST /ai/kb/{docId}/reindex}）。
 *
 * @author tbgbook
 */
@Data
public class KbReindexRequest
{
    /** 新的切片长度（可覆盖；缺省用文档现值） */
    private Integer chunkSize;

    /** 新的切片重叠长度 */
    private Integer chunkOverlap;

    /** 内容与参数均未变时是否强制重建，默认 false */
    private Boolean force;
}
