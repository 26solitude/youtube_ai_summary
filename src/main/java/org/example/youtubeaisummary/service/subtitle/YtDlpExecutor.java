package org.example.youtubeaisummary.service.subtitle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class YtDlpExecutor {

    private final String ytDlpPath;
    private final boolean proxyEnabled;
    private final String proxyUrl;

    public YtDlpExecutor(@Value("${app.ytdlp.path}") String ytDlpPath,
                         @Value("${proxy.enabled:false}") boolean proxyEnabled,
                         @Value("${proxy.url:}") String proxyUrl) {
        this.ytDlpPath = ytDlpPath;
        this.proxyEnabled = proxyEnabled;
        this.proxyUrl = proxyUrl;
    }


    /**
     * 명령어를 실행하고 표준 출력에서 JSON 한 줄을 반환합니다.
     */
    public String executeAndGetJson(String videoId) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(ytDlpPath, "--dump-json", "--no-warnings"));
        addProxyToCommandIfEnabled(command);

        command.add(videoId);
        return execute(command, true);
    }

    /**
     * 명령어를 실행하여 파일로 저장합니다.
     */
    public void executeAndSaveToFile(String videoId, String langCode, String outputTemplate) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                ytDlpPath,
                "--write-auto-sub",
                "--sub-lang", langCode,
                "--sub-format", "vtt",
                "--convert-subs", "vtt",
                "--skip-download",
                "-o", outputTemplate
        ));
        addProxyToCommandIfEnabled(command);
        command.add(videoId);
        execute(command, false);
    }

    // 프록시 옵션을 추가하는 로직
    private void addProxyToCommandIfEnabled(List<String> command) {
        if (proxyEnabled && proxyUrl != null && !proxyUrl.isEmpty()) {
            command.add("--proxy");
            command.add(proxyUrl);
            log.info("프록시를 사용하여 yt-dlp를 실행합니다.");
        }
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
                        break;
                    }
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("yt-dlp process exited with code {}. Command: {}", exitCode, String.join(" ", command));
            throw new IOException("yt-dlp process exited with code " + exitCode);
        }
        return output.toString();
    }
}