package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 库存快照生成 Quartz 触发器。
 */
@Component("stockSnapshotTask")
public class StockSnapshotTask
{
    private static final Logger log = LoggerFactory.getLogger(StockSnapshotTask.class);

    private final IScheduledJobService scheduledJobService;

    public StockSnapshotTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void generate()
    {
        JobRunResult result = scheduledJobService.run("stockSnapshotTask.generate");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("库存快照生成 completed, affected={}", result.getAffected());
    }
}
