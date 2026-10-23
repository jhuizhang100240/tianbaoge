package com.tbgbook.quartz.task.biz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.service.IScheduledJobService;

/**
 * 知识库索引超时检测 Quartz 触发器。
 */
@Component("kbIndexTimeoutTask")
public class KbIndexTimeoutTask
{
    private static final Logger log = LoggerFactory.getLogger(KbIndexTimeoutTask.class);

    private final IScheduledJobService scheduledJobService;

    public KbIndexTimeoutTask(IScheduledJobService scheduledJobService)
    {
        this.scheduledJobService = scheduledJobService;
    }

    public void check()
    {
        JobRunResult result = scheduledJobService.run("kbIndexTimeoutTask.check");
        if (!result.isSuccess())
        {
            throw new IllegalStateException(result.getMessage());
        }
        log.info("知识库索引超时检测 completed, affected={}", result.getAffected());
    }
}
