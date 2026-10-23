package com.tbgbook.bookstore.ai.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.AiAnnotation;

/**
 * AI 问答质量标注 Service。
 *
 * @author tbgbook
 */
public interface IAiQaService extends IService<AiAnnotation>
{
    List<AiAnnotation> selectAiAnnotationList(AiAnnotation query);

    int addAnnotation(AiAnnotation annotation);

    int updateAnnotation(AiAnnotation annotation);

    int removeAnnotations(Long[] annotationIds);

    /**
     * 标注 AI 回复（重复标注 409；category=1 时可带 relatedDocId 一键补文档）。
     */
    Map<String, Object> annotate(AiAnnotation request);
}
