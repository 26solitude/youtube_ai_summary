package org.example.youtubeaisummary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;


@EnableAsync
@SpringBootApplication
public class YoutubeAiSummaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoutubeAiSummaryApplication.class, args);
    }

}
