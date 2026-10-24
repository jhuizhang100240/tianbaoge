package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 报表订阅推送 Quartz 触发器。
 */
@Component("reportSubscribeTask")
public class ReportSubscribeTask
{
    private static final Logger log = LoggerFactory.getLogger(ReportSubscribeTask.class);

    private final IScheduledJobService scheduledJobService;

    public ReportSubscribeTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void push()
    {
        JobRunResult result = scheduledJobService.run("reportSubscribeTask.push");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("报表订阅推送 completed, affected={}", result.getAffected());
    }
}
