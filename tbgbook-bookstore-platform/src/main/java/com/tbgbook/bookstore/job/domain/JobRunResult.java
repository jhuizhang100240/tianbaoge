package com.tbgbook.bookstore.job.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 定时任务执行结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobRunResult
{
    private String jobCode;

    private boolean success;

    private int affected;

    private String message;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    public static JobRunResult ok(String jobCode, int affected, String message,
            LocalDateTime startedAt)
    {
        return new JobRunResult(jobCode, true, affected, message, startedAt, LocalDateTime.now());
    }

    public static JobRunResult failed(String jobCode, String message, LocalDateTime startedAt)
    {
        return new JobRunResult(jobCode, false, 0, message, startedAt, LocalDateTime.now());
    }
}
