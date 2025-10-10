package org.example.youtubeaisummary.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiClient.class);
    private final ChatClient chatClient;
    private final PromptManager promptManager;

    public OpenAiClient(ChatClient chatClient, PromptManager promptManager) {
        this.chatClient = chatClient;
        this.promptManager = promptManager;
    }

    @Retryable(
            retryFor = { RestClientException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 1.5)
    )
    public String getPartialSummary(String chunk) {
        logger.info("OpenAI API 호출 시도: 부분 요약 생성");
        String prompt = promptManager.getPartialSummaryPrompt(chunk);
        return chatClient.prompt().user(prompt).call().content();
    }

    @Retryable(
            retryFor = { RestClientException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000, multiplier = 2.0)
    )
    public String getFinalSummaryFromTranscript(String transcript) {
        logger.info("OpenAI API 호출 시도: 자막 원본으로 최종본 생성");
        String prompt = promptManager.getFinalFromTranscriptPrompt(transcript);
        return chatClient.prompt().user(prompt).call().content();
    }

    @Retryable(
            retryFor = { RestClientException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000, multiplier = 2.0)
    )
    public String getFinalSummaryFromSummaries(String summaries) {
        logger.info("OpenAI API 호출 시도: 부분 요약으로 최종본 생성");
        String prompt = promptManager.getFinalFromSummariesPrompt(summaries);
        return chatClient.prompt().user(prompt).call().content();
    }
}