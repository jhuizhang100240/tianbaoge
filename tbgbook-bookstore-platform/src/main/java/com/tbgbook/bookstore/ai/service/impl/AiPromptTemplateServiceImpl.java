package com.tbgbook.bookstore.ai.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.domain.AiPromptTemplate;
import com.tbgbook.bookstore.ai.domain.AiPromptVersion;
import com.tbgbook.bookstore.ai.domain.GrayAdjustRequest;
import com.tbgbook.bookstore.ai.mapper.AiPromptTemplateMapper;
import com.tbgbook.bookstore.ai.mapper.AiPromptVersionMapper;
import com.tbgbook.bookstore.ai.service.IAiPromptTemplateService;
import com.tbgbook.bookstore.common.exception.BizErrors;

import lombok.RequiredArgsConstructor;

/**
 * AI 提示词模板 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl extends ServiceImpl<AiPromptTemplateMapper, AiPromptTemplate>
        implements IAiPromptTemplateService
{
    private static final long STORE_ID = 1L;

    private final AiPromptVersionMapper aiPromptVersionMapper;

    @Override
    public List<AiPromptTemplate> selectAiPromptTemplateList(AiPromptTemplate query)
    {
        LambdaQueryWrapper<AiPromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPromptTemplate::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.eq(query.getScene() != null, AiPromptTemplate::getScene, query.getScene());
            wrapper.like(StringUtils.hasText(query.getTemplateName()),
                    AiPromptTemplate::getTemplateName, query.getTemplateName());
            wrapper.eq(query.getStatus() != null, AiPromptTemplate::getStatus, query.getStatus());
            wrapper.eq(query.getGrayType() != null, AiPromptTemplate::getGrayType, query.getGrayType());
        }
        wrapper.orderByDesc(AiPromptTemplate::getTemplateId);
        return list(wrapper);
    }

    @Override
    public int addTemplate(AiPromptTemplate template)
    {
        if (template == null || !StringUtils.hasText(template.getTemplateName()))
        {
            throw BizErrors.badRequest("模板名称不能为空");
        }
        if (template.getScene() == null || template.getScene() < 1 || template.getScene() > 5)
        {
            throw BizErrors.badRequest("场景取值 1-5");
        }
        template.setTemplateId(null);
        template.setStoreId(STORE_ID);
        template.setCurrentVersion("");
        template.setStatus(1);
        template.setGrayType(1);
        template.setGrayRatio(0);
        template.setGrayValue("");
        template.setDelFlag(null);
        return save(template) ? 1 : 0;
    }

    @Override
    public int updateTemplate(AiPromptTemplate template)
    {
        if (template == null || template.getTemplateId() == null)
        {
            throw BizErrors.badRequest("模板ID不能为空");
        }
        if (getById(template.getTemplateId()) == null)
        {
            throw BizErrors.notFound("模板", template.getTemplateId());
        }
        if (template.getScene() != null && (template.getScene() < 1 || template.getScene() > 5))
        {
            throw BizErrors.badRequest("场景取值 1-5");
        }
        template.setStoreId(null);
        template.setCurrentVersion(null);
        template.setStatus(null);
        template.setGrayRatio(null);
        template.setDelFlag(null);
        return updateById(template) ? 1 : 0;
    }

    @Override
    public int removeTemplates(Long[] templateIds)
    {
        if (templateIds == null || templateIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的模板");
        }
        int count = 0;
        for (Long templateId : templateIds)
        {
            if (getById(templateId) == null)
            {
                throw BizErrors.notFound("模板", templateId);
            }
            long versions = aiPromptVersionMapper.selectCount(new LambdaQueryWrapper<AiPromptVersion>()
                    .eq(AiPromptVersion::getTemplateId, templateId));
            if (versions > 0)
            {
                throw BizErrors.ruleViolated("该模板下存在 " + versions + " 个版本，不能删除；请先清理版本");
            }
            removeById(templateId);
            count++;
        }
        return count;
    }

    @Override
    public int adjustGray(Long templateId, GrayAdjustRequest request)
    {
        AiPromptTemplate template = getById(templateId);
        if (template == null)
        {
            throw BizErrors.notFound("模板", templateId);
        }
        if (!Integer.valueOf(2).equals(template.getStatus()))
        {
            throw BizErrors.ruleViolated("模板未处于灰度状态，不能调整灰度配置");
        }
        if (request == null || request.getGrayType() == null
                || (request.getGrayType() != 1 && request.getGrayType() != 2))
        {
            throw BizErrors.badRequest("灰度方式取值 1按比例 / 2按会员群");
        }
        if (request.getGrayType() == 1)
        {
            if (request.getGrayRatio() == null || request.getGrayRatio() < 1 || request.getGrayRatio() > 100)
            {
                throw BizErrors.ruleViolated("灰度比例不在 1~100 范围");
            }
        }
        else if (!StringUtils.hasText(request.getGrayValue()))
        {
            throw BizErrors.badRequest("按会员群灰度时，会员等级列表不能为空");
        }
        AiPromptTemplate update = new AiPromptTemplate();
        update.setTemplateId(templateId);
        update.setGrayType(request.getGrayType());
        update.setGrayRatio(request.getGrayType() == 1 ? request.getGrayRatio() : 0);
        update.setGrayValue(request.getGrayType() == 2 ? request.getGrayValue() : "");
        return updateById(update) ? 1 : 0;
    }
}
