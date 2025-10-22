package org.example.youtubeaisummary.service.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiClient {

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
        String prompt = promptManager.getPartialSummaryPrompt(chunk);
        return chatClientBuilder.defaultSystem(systemPrompt).build().prompt().user(prompt).call().content();
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000, multiplier = 2.0)
    )
    public String getFinalSummaryFromTranscript(String transcript) {
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
        String prompt = promptManager.getFinalFromSummariesPrompt(summaries);
        return chatClientBuilder.defaultSystem(systemPrompt).build().prompt().user(prompt).call().content();
    }
}