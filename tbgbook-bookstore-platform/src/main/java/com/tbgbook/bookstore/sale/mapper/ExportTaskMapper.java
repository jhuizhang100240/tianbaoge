package com.tbgbook.bookstore.sale.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 导出任务写入（**跨域写**：`biz_export_task` 属分析域 / 阶段八的下载中心）。
 *
 * <p>为什么销售域要写它：契约明写 `POST /sales/order/export` 「只创建任务并返回 taskId，
 * 前端跳 A-70 或轮询 `GET /analysis/export/{taskId}`」——**建任务是本端点的职责**，
 * 而文件的生成与下载属阶段八。故这里只 insert 一行 + 取回主键。
 */
public interface ExportTaskMapper
{
    /** 建任务（状态 1排队中）；`isSensitive=1` 表示含成本/敏感字段，需二次授权（NFR-S8） */
    int insertTask(@Param("taskName") String taskName,
            @Param("exportType") String exportType,
            @Param("isSensitive") int isSensitive,
            @Param("createBy") String createBy);

    /** 取刚插入任务的主键（同事务内，安全） */
    Long selectLastTaskId();
}
