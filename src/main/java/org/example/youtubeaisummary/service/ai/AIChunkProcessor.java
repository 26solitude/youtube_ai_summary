package org.example.youtubeaisummary.service.ai;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class AIChunkProcessor {

    private final OpenAiClient openAiClient;

    public AIChunkProcessor(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<String> getPartialSummary(String chunk) {
        String partialSummary = openAiClient.getPartialSummary(chunk);
        return CompletableFuture.completedFuture(partialSummary);
    }
}
