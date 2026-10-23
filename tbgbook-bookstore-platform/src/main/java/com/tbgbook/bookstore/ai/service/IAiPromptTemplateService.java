package com.tbgbook.bookstore.ai.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.AiPromptTemplate;
import com.tbgbook.bookstore.ai.domain.GrayAdjustRequest;

/**
 * AI 提示词模板 Service。
 *
 * @author tbgbook
 */
public interface IAiPromptTemplateService extends IService<AiPromptTemplate>
{
    List<AiPromptTemplate> selectAiPromptTemplateList(AiPromptTemplate query);

    int addTemplate(AiPromptTemplate template);

    int updateTemplate(AiPromptTemplate template);

    int removeTemplates(Long[] templateIds);

    /**
     * 调整灰度方式/比例/会员群（不改变版本内容）。
     */
    int adjustGray(Long templateId, GrayAdjustRequest request);
}
