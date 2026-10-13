package com.tbgbook.bookstore.ai.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.domain.AiAnnotation;
import com.tbgbook.bookstore.ai.domain.AiMessage;
import com.tbgbook.bookstore.ai.mapper.AiAnnotationMapper;
import com.tbgbook.bookstore.ai.mapper.AiMessageMapper;
import com.tbgbook.bookstore.ai.service.IAiQaService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * AI 问答质量标注 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AiQaServiceImpl extends ServiceImpl<AiAnnotationMapper, AiAnnotation> implements IAiQaService
{
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiMessageMapper aiMessageMapper;

    @Override
    public List<AiAnnotation> selectAiAnnotationList(AiAnnotation query)
    {
        LambdaQueryWrapper<AiAnnotation> wrapper = new LambdaQueryWrapper<>();
        if (query != null)
        {
            wrapper.eq(query.getSessionId() != null, AiAnnotation::getSessionId, query.getSessionId());
            wrapper.eq(query.getMessageId() != null, AiAnnotation::getMessageId, query.getMessageId());
            wrapper.eq(query.getAccuracy() != null, AiAnnotation::getAccuracy, query.getAccuracy());
            wrapper.eq(query.getCategory() != null, AiAnnotation::getCategory, query.getCategory());
            wrapper.eq(query.getStatus() != null, AiAnnotation::getStatus, query.getStatus());
            wrapper.eq(StringUtils.hasText(query.getAnnotateBy()), AiAnnotation::getAnnotateBy, query.getAnnotateBy());
        }
        wrapper.orderByDesc(AiAnnotation::getAnnotationId);
        return list(wrapper);
    }

    @Override
    public int addAnnotation(AiAnnotation annotation)
    {
        validate(annotation);
        AiMessage message = aiMessageMapper.selectById(annotation.getMessageId());
        if (message == null)
        {
            throw BizErrors.notFound("消息", annotation.getMessageId());
        }
        if (!Integer.valueOf(2).equals(message.getRole()))
        {
            throw BizErrors.ruleViolated("只能标注 AI 回复（role=2）");
        }
        if (count(new LambdaQueryWrapper<AiAnnotation>().eq(AiAnnotation::getMessageId, annotation.getMessageId())) > 0)
        {
            throw BizErrors.conflict("该消息已被标注");
        }
        annotation.setAnnotationId(null);
        annotation.setSessionId(message.getSessionId());
        annotation.setStatus(2);
        annotation.setAnnotateBy(currentUser());
        annotation.setAnnotateTime(LocalDateTime.now());
        return save(annotation) ? 1 : 0;
    }

    @Override
    public int updateAnnotation(AiAnnotation annotation)
    {
        if (annotation == null || annotation.getAnnotationId() == null)
        {
            throw BizErrors.badRequest("标注ID不能为空");
        }
        if (getById(annotation.getAnnotationId()) == null)
        {
            throw BizErrors.notFound("标注", annotation.getAnnotationId());
        }
        annotation.setSessionId(null);
        annotation.setMessageId(null);
        annotation.setAnnotateBy(null);
        annotation.setAnnotateTime(null);
        annotation.setStatus(null);
        return updateById(annotation) ? 1 : 0;
    }

    @Override
    public int removeAnnotations(Long[] annotationIds)
    {
        if (annotationIds == null || annotationIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的标注");
        }
        return baseMapper.deleteBatchIds(List.of(annotationIds));
    }

    @Override
    public Map<String, Object> annotate(AiAnnotation request)
    {
        if (request == null || request.getMessageId() == null)
        {
            throw BizErrors.badRequest("被标注的消息ID不能为空");
        }
        validate(request);
        AiMessage message = aiMessageMapper.selectById(request.getMessageId());
        if (message == null)
        {
            throw BizErrors.notFound("消息", request.getMessageId());
        }
        if (!Integer.valueOf(2).equals(message.getRole()))
        {
            throw BizErrors.ruleViolated("只能标注 AI 回复（role=2）");
        }
        if (count(new LambdaQueryWrapper<AiAnnotation>().eq(AiAnnotation::getMessageId, request.getMessageId())) > 0)
        {
            throw BizErrors.conflict("该消息已被标注");
        }
        AiAnnotation annotation = new AiAnnotation();
        annotation.setSessionId(message.getSessionId());
        annotation.setMessageId(request.getMessageId());
        annotation.setAccuracy(request.getAccuracy());
        annotation.setCategory(request.getCategory());
        annotation.setSuggestion(request.getSuggestion() == null ? "" : request.getSuggestion());
        annotation.setRelatedDocId(request.getRelatedDocId() == null ? 0L : request.getRelatedDocId());
        annotation.setAnnotateBy(currentUser());
        annotation.setAnnotateTime(LocalDateTime.now());
        annotation.setStatus(2);
        save(annotation);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("annotationId", annotation.getAnnotationId());
        data.put("status", 2);
        data.put("annotateBy", annotation.getAnnotateBy());
        data.put("annotateTime", annotation.getAnnotateTime().format(TIME_FORMAT));
        return data;
    }

    private void validate(AiAnnotation annotation)
    {
        if (annotation.getAccuracy() == null || annotation.getAccuracy() < 1 || annotation.getAccuracy() > 3)
        {
            throw BizErrors.badRequest("准确性取值 1准确 / 2部分准确 / 3错误");
        }
        if (annotation.getCategory() == null || annotation.getCategory() < 1 || annotation.getCategory() > 4)
        {
            throw BizErrors.badRequest("问题归类取值 1-4");
        }
    }

    private String currentUser()
    {
        try
        {
            String username = SecurityUtils.getUsername();
            return username == null ? "system" : username;
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
