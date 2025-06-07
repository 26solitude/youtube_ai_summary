package org.example.youtubeaisummary.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class YtDlpExecutor {

    @Value("${app.ytdlp.path}")
    private String ytDlpPath;

    /**
     * 명령어를 실행하고 표준 출력에서 JSON 한 줄을 반환합니다.
     */
    public String executeAndGetJson(String videoId) throws IOException, InterruptedException {
        List<String> command = List.of(ytDlpPath, "--dump-json", "--no-warnings", videoId);
        return execute(command, true);
    }

    /**
     * 명령어를 실행하여 파일로 저장합니다.
     */
    public void executeAndSaveToFile(String videoId, String langCode, String outputTemplate) throws IOException, InterruptedException {
        List<String> command = List.of(
                ytDlpPath,
                "--write-auto-sub",
                "--sub-lang", langCode,
                "--sub-format", "vtt",
                "--convert-subs", "vtt",
                "--skip-download",
                "-o", outputTemplate,
                videoId
        );
        execute(command, false);
    }

    private String execute(List<String> command, boolean returnJson) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (returnJson) {
                    String trimmedLine = line.trim();
                    if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}")) {
                        output.append(trimmedLine);
                        // JSON은 한 줄이므로 찾으면 바로 루프 종료
                        break;
                    }
                }
                // JSON을 반환하지 않는 경우, 스트림을 소모하기만 함
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("yt-dlp process exited with code " + exitCode + " for command: " + String.join(" ", command));
        }
        return output.toString();
    }
}