package org.example.youtubeaisummary.service;

import org.example.youtubeaisummary.service.ai.AIService;
import org.example.youtubeaisummary.service.subtitle.SubtitleService;
import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class OrchestrationService {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    private final SubtitleService subtitleService;
    private final AIService aiService;
    private final Executor aiTaskExecutor;

    public OrchestrationService(
            Map<String, SubtitleService> subtitleServiceImplementations,
            @Value("${app.subtitle.provider}") String subtitleProvider,
            AIService aiService,
            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.subtitleService = subtitleServiceImplementations.get(subtitleProvider);
        this.aiService = aiService;
        this.aiTaskExecutor = aiTaskExecutor;
        if (this.subtitleService == null) {
            throw new IllegalArgumentException("지원하지 않는 자막 제공자(provider)입니다: " + subtitleProvider);
        }
        logger.info("현재 설정된 자막 제공자: {}", subtitleProvider);
    }

    @Async("ioTaskExecutor")
    public void processYoutubeVideo(String jobId, YoutubeVideo video) {
        logger.info("작업 ID: {}: OrchestrationService: 영상 처리를 시작합니다. SubtitleService를 호출하여 자막을 가져옵니다.", jobId);
        CompletableFuture<String> subtitleFuture = subtitleService.fetchSubs(jobId, video);

        subtitleFuture.thenAcceptAsync(extractedText -> {
                    logger.info("작업 ID: {}: 자막 추출 성공 (텍스트 길이: {}). AI 요약을 시작합니다.", jobId, extractedText.length());
                    aiService.summarize(jobId, extractedText);
                }, aiTaskExecutor)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    logger.error("작업 ID: {}: OrchestrationService 관점에서 자막 추출 실패. 예외: {}", jobId, cause.getMessage(), cause);
                    return null;
                });
    }
}
