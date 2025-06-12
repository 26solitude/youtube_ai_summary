package org.example.youtubeaisummary.service.ai;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
public class PromptManager {
    private final PromptTemplate partialSummaryTemplate;
    private final PromptTemplate finalFromTranscriptTemplate;
    private final PromptTemplate finalFromSummariesTemplate;

    public PromptManager(@Value("${app.ai.prompt.partial-summary}") String partialPrompt,
                         @Value("${app.ai.prompt.final-from-transcript}") String finalFromTranscriptPrompt,
                         @Value("${app.ai.prompt.final-from-summaries}") String finalFromSummariesPrompt) {
        this.partialSummaryTemplate = new PromptTemplate(partialPrompt);
        this.finalFromTranscriptTemplate = new PromptTemplate(finalFromTranscriptPrompt);
        this.finalFromSummariesTemplate = new PromptTemplate(finalFromSummariesPrompt);
    }

    public String getPartialSummaryPrompt(String chunk) {
        return partialSummaryTemplate.render(Map.of("chunk", chunk));
    }

    public String getFinalFromTranscriptPrompt(String text) {
        return finalFromTranscriptTemplate.render(Map.of("text", text));
    }

    public String getFinalFromSummariesPrompt(String summaries) {
        return finalFromSummariesTemplate.render(Map.of("summaries", summaries));
    }
}