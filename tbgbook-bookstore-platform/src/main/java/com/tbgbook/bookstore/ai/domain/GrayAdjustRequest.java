package com.tbgbook.bookstore.ai.domain;

import lombok.Data;

/**
 * 调整灰度配置请求（{@code POST /ai/prompt/{templateId}/gray}）。
 *
 * @author tbgbook
 */
@Data
public class GrayAdjustRequest
{
    /** 1按比例 2按会员群 */
    private Integer grayType;

    private Integer grayRatio;

    private String grayValue;
}
