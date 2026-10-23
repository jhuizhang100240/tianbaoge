package com.tbgbook.bookstore.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.analysis.domain.ExportTask;

/**
 * 异步导出任务 Mapper（分析域）。
 *
 * <p>名字带 Analysis 前缀：销售域已有同名 `ExportTaskMapper`（只负责阶段四订单导出建任务），
 * MyBatis 按简单类名注册 bean，重名会启动冲突。
 */
public interface AnalysisExportTaskMapper extends BaseMapper<ExportTask>
{
}
