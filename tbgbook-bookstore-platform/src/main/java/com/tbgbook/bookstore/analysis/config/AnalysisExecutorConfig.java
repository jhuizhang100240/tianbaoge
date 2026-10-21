package com.tbgbook.bookstore.analysis.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 数据分析异步导出线程池。
 *
 * <p>固定 2 线程 + 有界队列 20：超限直接拒绝（429），避免导出把数据库与内存拖垮。
 *
 * @author tbgbook
 */
@Configuration
public class AnalysisExecutorConfig
{
    @Bean("analysisExportExecutor")
    public ThreadPoolTaskExecutor analysisExportExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("analysis-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
