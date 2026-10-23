package com.tbgbook.bookstore.analysis.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 工作台 Service（A-03/A-05）。
 */
public interface IDashboardService
{
    Map<String, Object> overview(LocalDate date, boolean costVisible);

    Map<String, Object> trend(String granularity, LocalDate startDate, LocalDate endDate, String compareType);

    Map<String, Object> screenData();
}
