package com.tbgbook.bookstore.job.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 业务定时任务入口实现；各任务在后续 Task 中接入既有服务。
 */
@Service
public class ScheduledJobServiceImpl implements IScheduledJobService
{
    @Override
    public JobRunResult run(String jobCode)
    {
        LocalDateTime startedAt = LocalDateTime.now();
        return JobRunResult.failed(jobCode, "not implemented", startedAt);
    }
}
