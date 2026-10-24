package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 库存预警扫描 Quartz 触发器。
 */
@Component("stockWarningTask")
public class StockWarningTask
{
    private static final Logger log = LoggerFactory.getLogger(StockWarningTask.class);

    private final IScheduledJobService scheduledJobService;

    public StockWarningTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void scan()
    {
        JobRunResult result = scheduledJobService.run("stockWarningTask.scan");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("库存预警扫描 completed, affected={}", result.getAffected());
    }
}
