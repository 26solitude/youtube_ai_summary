package org.example.youtubeaisummary.service;

import org.example.youtubeaisummary.dto.JobStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private final JobManager jobManager;
    private final AIChunkProcessor chunkProcessor;
    private final ChatClient chatClient;
    private final PromptManager promptManager;
    @Value("${app.ai.strategy.optimal-chars:12000}")
    private int optimalChunkCharSize;
    @Value("${app.ai.strategy.max-chunks:4}")
    private int maxChunks;

    public AIService(JobManager jobManager, AIChunkProcessor chunkProcessor, ChatClient chatClient, PromptManager promptManager) {
        this.jobManager = jobManager;
        this.chunkProcessor = chunkProcessor;
        this.chatClient = chatClient;
        this.promptManager = promptManager;
    }

    @Async("subtitleTaskExecutor")
    public void summarize(String jobId, String subtitleText) {
        try {
            // --- 3. '결정'과 '실행'으로 로직을 명확히 분리 ---
            SummarizationStrategy strategy = decideStrategy(subtitleText.length());
            String finalSummary = executeStrategy(jobId, subtitleText, strategy);

            if (finalSummary == null || finalSummary.isBlank()) {
                throw new RuntimeException("AI로부터 유효한 최종 요약 응답을 받지 못했습니다.");
            }
            jobManager.completeJob(jobId, finalSummary);

        } catch (Exception e) {
            jobManager.failJob(jobId, "AI 요약 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 오직 '전략 결정'만 책임지는 메서드
     */
    private SummarizationStrategy decideStrategy(int totalChars) {
        if (totalChars <= optimalChunkCharSize) {
            return new SummarizationStrategy(StrategyType.SINGLE_SHOT, totalChars);
        }

        int potentialChunks = (int) Math.ceil((double) totalChars / optimalChunkCharSize);
        if (potentialChunks <= maxChunks) {
            return new SummarizationStrategy(StrategyType.OPTIMAL_MAP_REDUCE, optimalChunkCharSize);
        } else {
            int dynamicChunkSize = (int) Math.ceil((double) totalChars / maxChunks);
            return new SummarizationStrategy(StrategyType.FIXED_MAP_REDUCE, dynamicChunkSize);
        }
    }

    /**
     * 결정된 전략을 '실행'만 하는 메서드 (switch 사용으로 가독성 향상)
     */
    private String executeStrategy(String jobId, String text, SummarizationStrategy strategy) {
        logger.info("작업 ID: {} - AI 요약 시작 (총 글자 수: {}, 전략: {})", jobId, text.length(), strategy.type());

        return switch (strategy.type()) {
            case SINGLE_SHOT -> {
                logger.info("작업 ID: {} - [전략 1] 단일 요청으로 정리합니다.", jobId);
                yield getFinalSummary(jobId, text, true);
            }
            case OPTIMAL_MAP_REDUCE -> {
                logger.info("작업 ID: {} - [전략 2] 최적 크기({}자) 청크로 분할합니다.", jobId, strategy.chunkSize());
                yield executeMapReduce(jobId, text, strategy.chunkSize());
            }
            case FIXED_MAP_REDUCE -> {
                logger.info("작업 ID: {} - [전략 3] 최대 청크 수({})에 맞춰 분할합니다.", jobId, maxChunks);
                yield executeMapReduce(jobId, text, strategy.chunkSize());
            }
        };
    }

    private String executeMapReduce(String jobId, String text, int chunkSize) {
        int overlap = (int) (chunkSize * 0.1);
        List<String> chunks = splitTextByChars(text, chunkSize, overlap);
        logger.info("작업 ID: {} - 텍스트가 {}개의 청크로 분할되었습니다.", jobId, chunks.size());

        jobManager.updateJobProgress(jobId, JobStatusDto.JobStatus.AI_SUMMARIZING_PARTIAL, "부분 요약들을 생성 중입니다...");

        List<CompletableFuture<String>> partialSummaryFutures = chunks.stream().map(chunkProcessor::getPartialSummary).collect(Collectors.toList());

        CompletableFuture.allOf(partialSummaryFutures.toArray(new CompletableFuture[0])).join();

        String combinedSummaries = partialSummaryFutures.stream().map(CompletableFuture::join).collect(Collectors.joining("\n\n"));

        return getFinalSummary(jobId, combinedSummaries, false);
    }

    private String getFinalSummary(String jobId, String content, boolean isFromTranscript) {
        jobManager.updateJobProgress(jobId, JobStatusDto.JobStatus.AI_SUMMARIZING_FINAL, "최종 요약본을 생성 중입니다...");
        String prompt = isFromTranscript ? promptManager.getFinalFromTranscriptPrompt(content) : promptManager.getFinalFromSummariesPrompt(content);
        return chatClient.prompt().user(prompt).call().content();
    }

    private List<String> splitTextByChars(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        int textLength = text.length();
        int i = 0;
        while (i < textLength) {
            int end = Math.min(i + chunkSize, textLength);
            chunks.add(text.substring(i, end));
            i += chunkSize - chunkOverlap;
        }
        return chunks;
    }

    private enum StrategyType {
        SINGLE_SHOT,
        OPTIMAL_MAP_REDUCE,
        FIXED_MAP_REDUCE
    }

    private record SummarizationStrategy(StrategyType type, int chunkSize) {
    }
}