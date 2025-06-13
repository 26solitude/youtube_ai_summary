package org.example.youtubeaisummary.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * 자막 추출, 파일 처리 등 I/O 중심 작업을 위한 스레드 풀
     */
    @Bean(name = "ioTaskExecutor")
    public Executor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int cores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = cores * 2;

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(Math.max(corePoolSize * 2, 20));

        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("IO-");
        executor.initialize();
        return executor;
    }

    /**
     * AI API 호출 등 네트워크 중심 작업을 위한 스레드 풀
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AI-");
        executor.initialize();
        return executor;
    }
}
