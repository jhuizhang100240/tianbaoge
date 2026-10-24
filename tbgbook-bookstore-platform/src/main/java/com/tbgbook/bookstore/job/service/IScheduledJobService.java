package com.tbgbook.bookstore.job.service;

import com.tbgbook.bookstore.job.domain.JobRunResult;

/**
 * 业务定时任务入口。
 */
public interface IScheduledJobService
{
    JobRunResult run(String jobCode);
}
