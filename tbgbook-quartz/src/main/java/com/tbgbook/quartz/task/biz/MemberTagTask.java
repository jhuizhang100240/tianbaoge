package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 会员标签重算 Quartz 触发器。
 */
@Component("memberTagTask")
public class MemberTagTask
{
    private static final Logger log = LoggerFactory.getLogger(MemberTagTask.class);

    private final IScheduledJobService scheduledJobService;

    public MemberTagTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void recalc()
    {
        JobRunResult result = scheduledJobService.run("memberTagTask.recalc");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("会员标签重算 completed, affected={}", result.getAffected());
    }
}
