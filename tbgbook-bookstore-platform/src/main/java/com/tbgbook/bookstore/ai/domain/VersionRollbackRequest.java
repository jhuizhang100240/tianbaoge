package com.tbgbook.bookstore.ai.domain;

import lombok.Data;

/**
 * 回滚提示词版本请求（{@code POST /ai/prompt/version/{versionId}/rollback}）。
 *
 * @author tbgbook
 */
@Data
public class VersionRollbackRequest
{
    private String reason;
}
