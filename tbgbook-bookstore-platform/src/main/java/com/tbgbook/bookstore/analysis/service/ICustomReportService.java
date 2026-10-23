package com.tbgbook.bookstore.analysis.service;

import java.util.Map;

import com.tbgbook.bookstore.analysis.domain.request.CustomReportRequest;

/**
 * 自定义报表 Service（FR-4.6.9）。
 */
public interface ICustomReportService
{
    Map<String, Object> run(CustomReportRequest request);
}
