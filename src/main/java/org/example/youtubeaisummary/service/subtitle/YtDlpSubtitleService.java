package org.example.youtubeaisummary.service.subtitle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.exception.subtitle.NoSubtitlesFoundException;
import org.example.youtubeaisummary.exception.subtitle.YoutubeApiException;
import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.min;

@Service("ytDlp")
public class YtDlpSubtitleService extends AbstractSubtitleService {


    // 역할에 따라 분리된 객체들을 주입받습니다.
    private final YtDlpExecutor ytDlpExecutor;
    private final FileManager fileManager;
    private final ObjectMapper objectMapper;
    private final SubtitleProcessor subtitleProcessor;

    public YtDlpSubtitleService(YtDlpExecutor ytDlpExecutor, FileManager fileManager, ObjectMapper objectMapper, SubtitleProcessor subtitleProcessor) {
        this.ytDlpExecutor = ytDlpExecutor;
        this.fileManager = fileManager;
        this.objectMapper = objectMapper;
        this.subtitleProcessor = subtitleProcessor;
    }

    @Override
    @Async("ioTaskExecutor")
    public CompletableFuture<String> fetchSubs(String jobId, YoutubeVideo video) {
        Path expectedSubtitlePath = null;
        try {
            // 1. 메타데이터 가져오기
            updateJobProgress(jobId, JobStatusDto.JobStatus.SUBTITLE_EXTRACTING, "자막 추출을 시작합니다...");
            String jsonOutput = ytDlpExecutor.executeAndGetJson(video.getVideoId());
            JsonNode videoInfo = objectMapper.readTree(jsonOutput);

            // 2. 언어 코드 결정
            String langCode = findBestSubtitleLanguage(videoInfo);

            // 3. 자막 다운로드
            Path tempDir = fileManager.getTempDir();
            expectedSubtitlePath = tempDir.resolve(video.getVideoId() + "." + langCode + ".srt");
            if (Files.notExists(tempDir)) Files.createDirectories(tempDir);
            String outputTemplate = tempDir.resolve("%(id)s.%(ext)s").toString();

            ytDlpExecutor.executeAndSaveToFile(video.getVideoId(), langCode, outputTemplate);

            // 4. 파일 읽고 정제하기
            String rawSubtitle = fileManager.readFileContent(expectedSubtitlePath);
            String cleanedText = subtitleProcessor.process(rawSubtitle);

            // 5. 성공 처리
            updateJobProgress(jobId, JobStatusDto.JobStatus.SUBTITLE_EXTRACTION_COMPLETED, cleanedText.substring(0, min(cleanedText.length(), 200)));
            return CompletableFuture.completedFuture(cleanedText);

        } catch (Exception e) {
            handleFailure(jobId, "자막 처리 중 오류 발생: " + e.getMessage(), e);
            return CompletableFuture.failedFuture(new YoutubeApiException("yt-dlp 자막 처리 실패", e));
        } finally {
            fileManager.deleteFile(expectedSubtitlePath);
        }
    }

    private String findBestSubtitleLanguage(JsonNode videoInfo) {
        String targetLang = videoInfo.path("language").asText(null);
        JsonNode automaticCaptions = videoInfo.path("automatic_captions");
        if (automaticCaptions.isMissingNode() || automaticCaptions.isEmpty()) {
            throw new NoSubtitlesFoundException("이 영상에는 자동 생성된 자막이 없습니다.");
        }

        List<String> availableLangs = new ArrayList<>();
        automaticCaptions.fieldNames().forEachRemaining(availableLangs::add);

        if ("ko".equals(targetLang) && availableLangs.contains("ko")) {
            return "ko";
        }
        if (availableLangs.contains("en")) {
            return "en";
        }
        if (!availableLangs.isEmpty()) {
            return availableLangs.getFirst();
        }
        throw new NoSubtitlesFoundException("처리 가능한 언어의 자동 생성 자막을 찾을 수 없습니다.");
    }

}