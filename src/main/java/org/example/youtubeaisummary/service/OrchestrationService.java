package org.example.youtubeaisummary.service;

import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class OrchestrationService {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    private final SubtitleService subtitleService;
    private final Executor subtitleTaskExecutor;

    public OrchestrationService(SubtitleService subtitleService, @Qualifier("subtitleTaskExecutor") Executor subtitleTaskExecutor) {
        this.subtitleService = subtitleService;
        this.subtitleTaskExecutor = subtitleTaskExecutor;
    }

    @Async("subtitleTaskExecutor")
    public void processYoutubeVideo(String jobId, YoutubeVideo video) {
        logger.info("작업 ID: {}: OrchestrationService: 영상 처리를 시작합니다. SubtitleService를 호출하여 자막을 가져옵니다.", jobId);
        CompletableFuture<String> subtitleFuture = subtitleService.fetchSubs(jobId, video);

        subtitleFuture.thenAcceptAsync(extractedText -> {
                    logger.info("작업 ID: {}: OrchestrationService: SubtitleService로부터 자막을 성공적으로 추출했습니다. 텍스트 길이: {}", jobId, extractedText.length()); // 한글로 변경
                }, subtitleTaskExecutor)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    logger.error("작업 ID: {}: OrchestrationService 관점에서 자막 추출 실패. 예외: {}", jobId, cause.getMessage(), cause);
                    return null;
                });

        logger.info("작업 ID: {}: OrchestrationService: 비동기 자막 추출 및 후속 처리가 예약되었습니다.", jobId);
    }
}
