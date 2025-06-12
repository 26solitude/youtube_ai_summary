package org.example.youtubeaisummary.service.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class AIChunkProcessor {

    private final ChatClient chatClient;
    private final PromptManager promptManager;

    public AIChunkProcessor(ChatClient chatClient, PromptManager promptManager) {
        this.chatClient = chatClient;
        this.promptManager = promptManager;
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<String> getPartialSummary(String chunk) {
        String prompt = promptManager.getPartialSummaryPrompt(chunk);
        String partialSummary = chatClient.prompt().user(prompt).call().content();
        return CompletableFuture.completedFuture(partialSummary);
    }
}
