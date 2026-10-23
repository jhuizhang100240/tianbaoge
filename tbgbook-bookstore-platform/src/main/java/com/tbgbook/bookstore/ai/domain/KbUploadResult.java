package com.tbgbook.bookstore.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 上传响应：docId + 索引状态 + 切片总数（契约 resp）。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class KbUploadResult
{
    private Long docId;

    private Integer indexStatus;

    private Integer chunkTotal;
}
