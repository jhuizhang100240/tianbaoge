package com.tbgbook.bookstore.ai.domain;

import lombok.Data;

/**
 * 发布提示词版本请求（{@code POST /ai/prompt/version/{versionId}/publish}）。
 *
 * <p>不传 grayType = 全量发布；传则进入灰度。
 *
 * @author tbgbook
 */
@Data
public class VersionPublishRequest
{
    private Integer grayType;

    private Integer grayRatio;

    private String grayValue;
}
