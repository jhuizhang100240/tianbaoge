package com.tbgbook.bookstore.ai.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提示词解析结果（命中模板版本或内置兜底）。
 *
 * @author tbgbook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptResolution
{
    private Long templateId;

    private Long versionId;

    /** 版本号；内置兜底模板为 {@code builtin} */
    private String versionNo;

    private String content;
}
