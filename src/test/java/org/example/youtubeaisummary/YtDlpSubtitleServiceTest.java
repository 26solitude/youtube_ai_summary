package org.example.youtubeaisummary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.exception.subtitle.NoSubtitlesFoundException;
import org.example.youtubeaisummary.exception.subtitle.YoutubeApiException;
import org.example.youtubeaisummary.repository.InMemoryJobRepository;
import org.example.youtubeaisummary.service.JobManager;
import org.example.youtubeaisummary.service.SseNotificationService;
import org.example.youtubeaisummary.service.subtitle.FileManager;
import org.example.youtubeaisummary.service.subtitle.SubtitleProcessor;
import org.example.youtubeaisummary.service.subtitle.YtDlpExecutor;
import org.example.youtubeaisummary.service.subtitle.YtDlpSubtitleService;
import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YtDlpSubtitleServiceTest {

    private final String testJobId = "test-job-123";
    private final String testVideoId = "testVideoId";
    private final YoutubeVideo testVideo = new YoutubeVideo("https://www.youtube.com/watch?v=" + testVideoId);
    @Mock
    private YtDlpExecutor mockYtDlpExecutor;
    @Mock
    private FileManager mockFileManager;
    @Mock
    private ObjectMapper mockObjectMapper;
    @Mock
    private InMemoryJobRepository mockJobRepository;
    @Mock
    private SseNotificationService mockSseNotificationService;
    @Mock
    private SubtitleProcessor mockSubtitleProcessor;

    @InjectMocks
    private YtDlpSubtitleService subtitleService;
    private JobManager jobManager;

    @BeforeEach
    void setUp() {
        jobManager = new JobManager(mockJobRepository, mockSseNotificationService);

        subtitleService.setJobManager(jobManager);
    }

    @Test
    @DisplayName("성공: 전체 자막 추출 및 정제 과정")
    void fetchSubs_Success() throws Exception {
        // Arrange
        String fakeJson = "{\"language\": \"ko\", \"automatic_captions\": {\"ko\": []}}";
        JsonNode fakeJsonNode = new ObjectMapper().readTree(fakeJson);
        String rawSubtitleText = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n안녕하세요";
        String processedText = "정제된 최종 텍스트"; // <-- 1. 예상되는 최종 결과 정의
        Path fakePath = Path.of("fake/path");

        when(mockYtDlpExecutor.executeAndGetJson(anyString())).thenReturn(fakeJson);
        when(mockObjectMapper.readTree(fakeJson)).thenReturn(fakeJsonNode);
        when(mockFileManager.getTempDir()).thenReturn(fakePath);
        when(mockFileManager.readFileContent(any(Path.class))).thenReturn(rawSubtitleText);
        when(mockSubtitleProcessor.process(rawSubtitleText)).thenReturn(processedText); // <-- 2. Mockito 행동 정의

        // Act
        String result = subtitleService.fetchSubs(testJobId, testVideo).get();

        // Assert
        assertEquals(processedText, result); // <-- 3. 최종 결과가 정제된 텍스트인지 검증

        // Verify
        verify(mockYtDlpExecutor).executeAndGetJson(testVideoId);
        verify(mockYtDlpExecutor).executeAndSaveToFile(eq(testVideoId), eq("ko"), anyString());
        verify(mockFileManager).readFileContent(any(Path.class));
        verify(mockSubtitleProcessor).process(rawSubtitleText); // <-- 4. processor가 호출되었는지 검증
        verify(mockFileManager).deleteFile(any(Path.class));
        verify(mockJobRepository, times(1)).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.SUBTITLE_EXTRACTING), anyString());
        verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.SUBTITLE_EXTRACTION_COMPLETED), eq(processedText)); // <-- 5. 최종 결과로 상태가 업데이트 되었는지 검증
    }

    @Test
    @DisplayName("예외: 자동 생성 자막 정보가 없을 때")
    void fetchSubs_ThrowsException_WhenNoAutoCaptions() throws IOException, InterruptedException {
        // Arrange
        String fakeJson = "{\"language\": \"ko\", \"automatic_captions\": {}}";
        JsonNode fakeJsonNode = new ObjectMapper().readTree(fakeJson);
        when(mockYtDlpExecutor.executeAndGetJson(anyString())).thenReturn(fakeJson);
        when(mockObjectMapper.readTree(fakeJson)).thenReturn(fakeJsonNode);

        // Act & Assert
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            subtitleService.fetchSubs(testJobId, testVideo).get();
        });

        assertInstanceOf(YoutubeApiException.class, exception.getCause());
        assertInstanceOf(NoSubtitlesFoundException.class, exception.getCause().getCause());
        assertEquals("이 영상에는 자동 생성된 자막이 없습니다.", exception.getCause().getCause().getMessage());
        verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.FAILED), anyString());
    }

    @Test
    @DisplayName("예외: yt-dlp가 파일을 생성하지 않았을 때")
    void fetchSubs_ThrowsException_WhenFileNotCreated() throws IOException, InterruptedException {
        // Arrange
        String fakeJson = "{\"language\": \"ko\", \"automatic_captions\": {\"ko\": []}}";
        JsonNode fakeJsonNode = new ObjectMapper().readTree(fakeJson);
        Path fakePath = Path.of("fake/path");

        when(mockYtDlpExecutor.executeAndGetJson(anyString())).thenReturn(fakeJson);
        when(mockObjectMapper.readTree(fakeJson)).thenReturn(fakeJsonNode);
        when(mockFileManager.getTempDir()).thenReturn(fakePath);
        when(mockFileManager.readFileContent(any(Path.class))).thenThrow(new NoSubtitlesFoundException("자막 파일이 생성되지 않았거나 내용이 비어있습니다."));

        // Act & Assert
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            subtitleService.fetchSubs(testJobId, testVideo).get();
        });

        assertInstanceOf(YoutubeApiException.class, exception.getCause());
        assertInstanceOf(NoSubtitlesFoundException.class, exception.getCause().getCause());
        verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.FAILED), anyString());
        verify(mockFileManager).deleteFile(any());
    }
}