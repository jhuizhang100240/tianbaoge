package com.tbgbook.bookstore.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbgbook.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 提示词模板（biz_ai_prompt_template）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_ai_prompt_template")
public class AiPromptTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    private Long storeId;

    /** 1智能客服 2图书推荐 3摘要生成 4经营问答 5选品建议 */
    private Integer scene;

    private String templateName;

    private String currentVersion;

    /** 1草稿 2灰度 3全量 4已下线 */
    private Integer status;

    /** 1按比例 2按会员群 */
    private Integer grayType;

    private Integer grayRatio;

    private String grayValue;

    @TableLogic
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String delFlag;
}
