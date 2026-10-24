package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 借阅到期与催还提醒 Quartz 触发器。
 */
@Component("borrowDueTask")
public class BorrowDueTask
{
    private static final Logger log = LoggerFactory.getLogger(BorrowDueTask.class);

    private final IScheduledJobService scheduledJobService;

    public BorrowDueTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void dueNotify()
    {
        JobRunResult result = scheduledJobService.run("borrowDueTask.dueNotify");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("借阅到期与催还提醒 completed, affected={}", result.getAffected());
    }
}
