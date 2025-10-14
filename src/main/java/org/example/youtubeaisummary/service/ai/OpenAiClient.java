package org.example.youtubeaisummary.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiClient.class);
    private final ChatClient.Builder chatClientBuilder;
    private final PromptManager promptManager;

    @Value("${app.ai.prompt.system}")
    private String systemPrompt;

    public OpenAiClient(ChatClient.Builder chatClientBuilder, PromptManager promptManager) {
        this.chatClientBuilder = chatClientBuilder;
        this.promptManager = promptManager;
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 1.5)
    )
    public String getPartialSummary(String chunk) {
        logger.info("OpenAI API 호출 시도: 부분 요약 생성");
        String prompt = promptManager.getPartialSummaryPrompt(chunk);
        return chatClientBuilder.defaultSystem(systemPrompt).build().prompt().user(prompt).call().content();
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000, multiplier = 2.0)
    )
    public String getFinalSummaryFromTranscript(String transcript) {
        logger.info("OpenAI API 호출 시도: 자막 원본으로 최종본 생성");
        String text = promptManager.getFinalFromTranscriptPrompt(transcript);
        System.out.println(text);
        return chatClientBuilder.defaultSystem(systemPrompt).build().prompt().user(text).call().content();
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000, multiplier = 2.0)
    )
    public String getFinalSummaryFromSummaries(String summaries) {
        logger.info("OpenAI API 호출 시도: 부분 요약으로 최종본 생성");
        String prompt = promptManager.getFinalFromSummariesPrompt(summaries);
        return chatClientBuilder.defaultSystem(systemPrompt).build().prompt().user(prompt).call().content();
    }
}