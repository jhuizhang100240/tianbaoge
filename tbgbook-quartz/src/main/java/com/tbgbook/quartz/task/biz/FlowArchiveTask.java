package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 流水归档 Quartz 触发器。
 */
@Component("flowArchiveTask")
public class FlowArchiveTask
{
    private static final Logger log = LoggerFactory.getLogger(FlowArchiveTask.class);

    private final IScheduledJobService scheduledJobService;

    public FlowArchiveTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void archive()
    {
        JobRunResult result = scheduledJobService.run("flowArchiveTask.archive");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("流水归档 completed, affected={}", result.getAffected());
    }
}
