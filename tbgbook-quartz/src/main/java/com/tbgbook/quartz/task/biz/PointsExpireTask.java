package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 积分到期提醒 Quartz 触发器。
 */
@Component("pointsExpireTask")
public class PointsExpireTask
{
    private static final Logger log = LoggerFactory.getLogger(PointsExpireTask.class);

    private final IScheduledJobService scheduledJobService;

    public PointsExpireTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void expireNotify()
    {
        JobRunResult result = scheduledJobService.run("pointsExpireTask.expireNotify");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("积分到期提醒 completed, affected={}", result.getAffected());
    }
}
