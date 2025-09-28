package org.example.youtubeaisummary.service.subtitle;

import org.springframework.stereotype.Component;


import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

@Component
public class SubtitleProcessor {

    public record ProcessedSentence(String startTime, String sentence) {}

    // 문장을 나누는 최대 길이 (이 값을 조절하여 결과물 튜닝 가능)
    private static final int MAX_SENTENCE_LENGTH = 200;

    public String process(String rawSubtitle) {
        List<ProcessedSentence> finalSentences = new ArrayList<>();
        StringBuilder sentenceBuffer = new StringBuilder();
        String bufferStartTime = null;
        String lastBlockText = "";

        Scanner scanner = new Scanner(rawSubtitle);
        scanner.useDelimiter("(\r?\n){2,}");

        while (scanner.hasNext()) {
            String block = scanner.next();
            String[] lines = block.split("\r?\n");
            if (lines.length < 2) continue;

            String startTime = lines[1].split(" --> ")[0].trim();
            String text = getTextFromBlock(lines).replaceAll("\\[.*?\\]", "").trim();
            if (text.isEmpty()) continue;

            String phraseToAdd = findNewPhrase(lastBlockText, text);
            lastBlockText = text;

            if (sentenceBuffer.isEmpty() && !phraseToAdd.isEmpty()) {
                bufferStartTime = startTime;
            }
            if (!phraseToAdd.isEmpty()) {
                if (!sentenceBuffer.isEmpty()) sentenceBuffer.append(" ");
                sentenceBuffer.append(phraseToAdd);
            }

            // 길이 기반 문장 분리 로직
            while (sentenceBuffer.length() > MAX_SENTENCE_LENGTH) {
                String currentText = sentenceBuffer.toString();
                int splitIndex = -1;

                // 1순위: 최대 길이 근처에서 뒤쪽부터 마침표(.)를 탐색합니다.
                // 탐색 범위를 문장 길이 + 20자 정도 여유를 줍니다.
                int searchLimit = Math.min(currentText.length(), MAX_SENTENCE_LENGTH + 20);
                splitIndex = currentText.substring(0, searchLimit).lastIndexOf('.');

                // 2순위: 마침표가 없다면, 같은 범위에서 공백(' ')을 탐색합니다.
                if (splitIndex == -1) {
                    splitIndex = currentText.substring(0, searchLimit).lastIndexOf(' ');
                }

                // 3순위: 마침표와 공백이 모두 없다면(예: 매우 긴 한 단어), 그냥 최대 길이에서 자릅니다.
                if (splitIndex == -1) {
                    splitIndex = MAX_SENTENCE_LENGTH;
                }

                // 찾은 분절 지점을 기준으로 문장을 완성합니다.
                String completedSentence = currentText.substring(0, splitIndex + 1).trim();
                finalSentences.add(new ProcessedSentence(bufferStartTime, completedSentence));

                // 버퍼에서 완성된 문장 부분을 제거하고, 남은 부분으로 새 문장을 시작합니다.
                sentenceBuffer.delete(0, splitIndex + 1);

                // 새 문장의 시작 시간은 대략적인 시간으로 업데이트합니다.
                if (!sentenceBuffer.isEmpty()) {
                    bufferStartTime = startTime; // 현재 처리 중인 블록의 시작 시간을 대략적으로 할당
                }
            }
        }
        scanner.close();

        if (!sentenceBuffer.isEmpty()) {
            finalSentences.add(new ProcessedSentence(bufferStartTime, sentenceBuffer.toString().trim()));
        }

        return finalSentences.stream()
                .map(p -> p.startTime() + ":" + p.sentence())
                .collect(Collectors.joining("\n"));
    }

    // 겹치지 않는 새 구절을 찾는 메서드
    private String findNewPhrase(String context, String newText) {
        if (context == null || context.isEmpty()) return newText;
        int overlapLength = 0;
        for (int i = 1; i <= Math.min(context.length(), newText.length()); i++) {
            if (context.endsWith(newText.substring(0, i))) {
                overlapLength = i;
            }
        }
        return newText.substring(overlapLength);
    }

    private String getTextFromBlock(String[] lines) {
        StringBuilder textBuilder = new StringBuilder();
        if (lines.length > 2) {
            for (int i = 2; i < lines.length; i++) {
                textBuilder.append(lines[i]).append(" ");
            }
        }
        return textBuilder.toString().trim();
    }
}