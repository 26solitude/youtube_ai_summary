package org.example.youtubeaisummary.config;

import io.github.thoroldvix.api.YoutubeTranscriptApi;
import io.github.thoroldvix.internal.TranscriptApiFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public YoutubeTranscriptApi youtubeTranscriptApi() {
        return TranscriptApiFactory.createDefault();
    }
}
