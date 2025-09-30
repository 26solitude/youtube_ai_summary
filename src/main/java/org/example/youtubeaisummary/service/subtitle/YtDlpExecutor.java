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
    private final String cookieFilePath;

    public YtDlpExecutor(@Value("${app.ytdlp.path}") String ytDlpPath,
                         @Value("${proxy.enabled:false}") boolean proxyEnabled,
                         @Value("${proxy.url:}") String proxyUrl,
                         @Value("${app.ytdlp.cookie-path:}") String cookieFilePath) {
        this.ytDlpPath = ytDlpPath;
        this.proxyEnabled = proxyEnabled;
        this.proxyUrl = proxyUrl;
        this.cookieFilePath = cookieFilePath;
    }


    /**
     * 명령어를 실행하고 표준 출력에서 JSON 한 줄을 반환합니다.
     */
    public String executeAndGetJson(String videoId) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(ytDlpPath, "--dump-single-json", "--no-warnings"));
        addProxyToCommandIfEnabled(command);
        addCookieToCommandIfEnabled(command);
        command.add(videoId);

        String processOutput = execute(command);

        // 실행 결과에서 JSON 라인을 찾아 반환합니다.
        if (processOutput.trim().startsWith("{") && processOutput.trim().endsWith("}")) {
            return processOutput.trim();
        }
        throw new IOException("yt-dlp로부터 유효한 JSON 출력을 찾지 못했습니다.");
    }

    /**
     * 자막 파일을 다운로드합니다.
     */
    public void executeAndSaveToFile(String videoId, String langCode, String outputTemplate) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                ytDlpPath,
                "--write-auto-subs",
                "--sub-lang", langCode,
                "--convert-subs", "srt",
                "--skip-download",
                "-o", outputTemplate
        ));
        addCookieToCommandIfEnabled(command);

        command.add(videoId);

        // 명령어를 실행하고, 이 메서드는 파일 저장이 목적이므로 출력은 무시합니다.
        execute(command);
    }

    /**
     * yt-dlp 명령어를 실행하고 전체 출력을 문자열로 반환하는 private 헬퍼 메서드입니다.
     * 이 메서드는 오직 '명령어 실행'과 '결과 반환' 책임만 가집니다.
     */
    private String execute(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder fullOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fullOutput.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("yt-dlp process exited with code {}. Command: {}\n--- yt-dlp output ---\n{}",
                    exitCode, String.join(" ", command), fullOutput.toString());
            throw new IOException("yt-dlp process exited with code " + exitCode);
        }

        return fullOutput.toString();
    }

    private void addProxyToCommandIfEnabled(List<String> command) {
        if (proxyEnabled && proxyUrl != null && !proxyUrl.isEmpty()) {
            command.add("--proxy");
            command.add(proxyUrl);
            log.info("프록시를 사용하여 yt-dlp를 실행합니다.");
        }
    }

    private void addCookieToCommandIfEnabled(List<String> command) {
        if (cookieFilePath != null && !cookieFilePath.isEmpty()) {
            command.add("--cookies");
            command.add(cookieFilePath);
            log.info("쿠키 파일을 사용하여 yt-dlp를 실행합니다: {}", cookieFilePath);
        }
    }
}