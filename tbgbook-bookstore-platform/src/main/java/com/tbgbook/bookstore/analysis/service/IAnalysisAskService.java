package com.tbgbook.bookstore.analysis.service;

import java.util.Map;

import com.tbgbook.bookstore.analysis.domain.request.AnalysisAskRequest;

/**
 * AI 经营数据问答 Service（FR-4.6.10 / FR-4.7.5）。
 */
public interface IAnalysisAskService
{
    Map<String, Object> ask(AnalysisAskRequest request);
}
