package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 看板指标预计算 Quartz 触发器。
 */
@Component("dashboardPrecomputeTask")
public class DashboardPrecomputeTask
{
    private static final Logger log = LoggerFactory.getLogger(DashboardPrecomputeTask.class);

    private final IScheduledJobService scheduledJobService;

    public DashboardPrecomputeTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void run()
    {
        JobRunResult result = scheduledJobService.run("dashboardPrecomputeTask.run");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("看板指标预计算 completed, affected={}", result.getAffected());
    }
}
