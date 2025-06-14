package org.example.youtubeaisummary.config;

import io.github.thoroldvix.api.YoutubeTranscriptApi;
import io.github.thoroldvix.internal.TranscriptApiFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public YoutubeTranscriptApi youtubeTranscriptApi() {
        return TranscriptApiFactory.createDefault();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        final String systemPrompt = "You are a professional editor who perfectly understands and writes in Korean. Your sole purpose is to restructure the provided Korean script into a formal, well-written Korean article. You must respond only in Korean under all circumstances. Do not add any extra comments, greetings, or questions. Just provide the final restructured Korean text.";

        return builder
                .defaultSystem(systemPrompt)
                .build();
    }
}
