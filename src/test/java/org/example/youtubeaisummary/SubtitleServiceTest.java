package org.example.youtubeaisummary;

import io.github.thoroldvix.api.*;
import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.exception.NoSubtitlesFoundException;
import org.example.youtubeaisummary.exception.YoutubeApiException;
import org.example.youtubeaisummary.repository.InMemoryJobRepository;
import org.example.youtubeaisummary.service.SseNotificationService;
import org.example.youtubeaisummary.service.SubtitleService;
import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubtitleServiceTest {

    private final String testJobId = "test-job-123";
    private final String testVideoId = "testVideoId";
    @Mock
    private YoutubeTranscriptApi mockApi;
    @Mock
    private InMemoryJobRepository mockJobRepository;
    @Mock
    private SseNotificationService mockSseNotificationService;
    @InjectMocks
    private SubtitleService subtitleService;
    private YoutubeVideo testVideo;

    @BeforeEach
    void setUp() {
        testVideo = mock(YoutubeVideo.class);
        when(testVideo.getVideoId()).thenReturn(testVideoId);
    }

    // --- ArgumentMatcher 구현체들 ---

    // --- 특정 메시지를 가진 "progress" 이벤트 검증 헬퍼 ---
    private void verifySpecificProgressEvent(String expectedMessage) {
        // JobRepository 업데이트 검증
        verify(mockJobRepository, times(1)).updateJob(
                eq(testJobId),
                eq(JobStatusDto.JobStatus.PROCESSING),
                eq(expectedMessage)
        );
        // SSE 이벤트 전송 검증
        verify(mockSseNotificationService, times(1)).sendEvent(
                eq(testJobId),
                eq("progress"),
                argThat(new ProgressJobStatusDtoMatcher(testJobId, expectedMessage))
        );
    }

    // --- 실패 시 공통 검증 헬퍼 ---
    private void verifyFailure(String expectedUserMessage, Class<? extends Exception> expectedExceptionClassForSse) {
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);

        verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.FAILED), eq(expectedUserMessage));
        verify(mockSseNotificationService).sendEvent(
                eq(testJobId),
                eq("error"),
                argThat(new ErrorJobStatusDtoMatcher(testJobId, expectedUserMessage))
        );
        verify(mockSseNotificationService).errorStream(eq(testJobId), exceptionCaptor.capture());

        assertTrue(expectedExceptionClassForSse.isInstance(exceptionCaptor.getValue()));

        verify(mockSseNotificationService, never()).sendEvent(eq(testJobId), eq("complete"), any());
        verify(mockSseNotificationService, never()).completeStream(eq(testJobId));
    }

    // --- 성공 경로 테스트 ---
    @Test
    @DisplayName("성공: 자막 추출 및 모든 과정 정상 완료")
    void fetchSubs_successPath() throws TranscriptRetrievalException {
        // Arrange
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        Transcript mockTranscript = mock(Transcript.class);
        TranscriptContent mockTranscriptContent = mock(TranscriptContent.class);
        // String expectedFormattedResult = "Formatted transcript text"; // 실제 format 결과와 비교하려면 필요

        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.of(mockTranscript).spliterator());
        when(mockTranscript.isGenerated()).thenReturn(true);
        when(mockTranscript.fetch()).thenReturn(mockTranscriptContent);
        // 실제 TextFormatter의 format 결과를 모킹하지 않으므로,
        // JobStatusDto.result()의 구체적인 문자열 값 비교는 아래 verify에서 anyString()으로 처리

        // Act
        subtitleService.fetchSubs(testJobId, testVideo);

        // Assert
        InOrder inOrder = inOrder(mockJobRepository, mockSseNotificationService);

        // 1. 첫 번째 progress 업데이트 검증
        inOrder.verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 추출을 시작합니다..."));
        inOrder.verify(mockSseNotificationService).sendEvent(
                eq(testJobId),
                eq("progress"),
                argThat(new ProgressJobStatusDtoMatcher(testJobId, "자막 추출을 시작합니다..."))
        );

        // 2. 두 번째 progress 업데이트 검증
        inOrder.verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 정보를 가져왔습니다. 내용 추출 중..."));
        inOrder.verify(mockSseNotificationService).sendEvent(
                eq(testJobId),
                eq("progress"),
                argThat(new ProgressJobStatusDtoMatcher(testJobId, "자막 정보를 가져왔습니다. 내용 추출 중..."))
        );

        // 3. 최종 완료 상태 검증
        inOrder.verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.COMPLETED), anyString());
        inOrder.verify(mockSseNotificationService).sendEvent(
                eq(testJobId),
                eq("complete"),
                argThat(new CompleteJobStatusDtoMatcher(testJobId)) // 결과 문자열은 not null만 확인
        );
        inOrder.verify(mockSseNotificationService).completeStream(eq(testJobId));

        // 에러 관련 메서드는 호출되지 않았음을 보장
        verify(mockSseNotificationService, never()).sendEvent(eq(testJobId), eq("error"), any());
        verify(mockSseNotificationService, never()).errorStream(eq(testJobId), any());
    }

    @Test
    @DisplayName("오류: listTranscripts 시 TranscriptRetrievalException (video unavailable)")
    void fetchSubs_listTranscripts_throwsTRE_videoUnavailable() throws TranscriptRetrievalException {
        TranscriptRetrievalException treException = new TranscriptRetrievalException("Simulated video unavailable or not found");
        when(mockApi.listTranscripts(testVideoId)).thenThrow(treException);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("영상을 찾을 수 없거나 접근할 수 없습니다.", NoSubtitlesFoundException.class);
    }

    @Test
    @DisplayName("오류: listTranscripts 시 TranscriptRetrievalException (subtitles disabled)")
    void fetchSubs_listTranscripts_throwsTRE_subtitlesDisabled() throws TranscriptRetrievalException {
        TranscriptRetrievalException treException = new TranscriptRetrievalException("Simulated subtitles disabled for this video");
        when(mockApi.listTranscripts(testVideoId)).thenThrow(treException);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("이 영상에는 자막 기능이 비활성화되어 있습니다.", NoSubtitlesFoundException.class);
    }

    @Test
    @DisplayName("오류: listTranscripts 시 TranscriptRetrievalException (기타 원인)")
    void fetchSubs_listTranscripts_throwsTRE_otherReason() throws TranscriptRetrievalException {
        TranscriptRetrievalException treException = new TranscriptRetrievalException("Some other transcript retrieval error");
        when(mockApi.listTranscripts(testVideoId)).thenThrow(treException);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("유튜브 자막 목록 조회 중 오류가 발생했습니다: " + treException.getMessage(), YoutubeApiException.class);
    }


    // --- 오류 시나리오별 테스트 케이스 ---
    // (각 테스트 메서드 내에서 verifySpecificProgressUpdate 또는 verifyFailure 호출 부분은 이전과 동일하게 유지)

    @Test
    @DisplayName("오류: listTranscripts 시 RuntimeException 발생")
    void fetchSubs_listTranscripts_throwsRuntimeException() throws TranscriptRetrievalException {
        RuntimeException runtimeException = new RuntimeException("Simulated runtime error during listTranscripts");
        when(mockApi.listTranscripts(testVideoId)).thenThrow(runtimeException);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("유튜브 자막 목록 조회 중 예기치 못한 오류가 발생했습니다.", YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: listTranscripts가 null 반환")
    void fetchSubs_listTranscripts_returnsNull() throws TranscriptRetrievalException {
        when(mockApi.listTranscripts(testVideoId)).thenReturn(null);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("유튜브로부터 자막 목록을 가져오지 못했습니다 (null 응답).", YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: 자동 생성 자막 없음")
    void fetchSubs_noAutoGeneratedTranscript() throws TranscriptRetrievalException {
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.<Transcript>empty().spliterator());
        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("이 영상에는 자동 생성된 자막이 없습니다.", NoSubtitlesFoundException.class);
    }

    @Test
    @DisplayName("오류: fetch() 시 TranscriptRetrievalException 발생")
    void fetchSubs_fetchContent_throwsTRE() throws TranscriptRetrievalException {
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        Transcript mockTranscript = mock(Transcript.class);

        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.of(mockTranscript).spliterator());
        when(mockTranscript.isGenerated()).thenReturn(true);

        TranscriptRetrievalException treException = new TranscriptRetrievalException("Simulated fetch content error");
        when(mockTranscript.fetch()).thenThrow(treException);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifySpecificProgressEvent("자막 정보를 가져왔습니다. 내용 추출 중...");
        verifyFailure("자막 내용을 가져오는 중 오류가 발생했습니다: " + treException.getMessage(), YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: fetch() 시 RuntimeException 발생")
    void fetchSubs_fetchContent_throwsRuntimeException() throws TranscriptRetrievalException {
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        Transcript mockTranscript = mock(Transcript.class);

        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.of(mockTranscript).spliterator());
        when(mockTranscript.isGenerated()).thenReturn(true);

        RuntimeException runtimeException = new RuntimeException("Simulated runtime error during fetch");
        when(mockTranscript.fetch()).thenThrow(runtimeException);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifySpecificProgressEvent("자막 정보를 가져왔습니다. 내용 추출 중...");
        verifyFailure("자막 내용을 가져오는 중 예기치 못한 오류가 발생했습니다.", YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: fetch()가 null 반환")
    void fetchSubs_fetchContent_returnsNull() throws TranscriptRetrievalException {
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        Transcript mockTranscript = mock(Transcript.class);

        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.of(mockTranscript).spliterator());
        when(mockTranscript.isGenerated()).thenReturn(true);
        when(mockTranscript.fetch()).thenReturn(null);

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifySpecificProgressEvent("자막 정보를 가져왔습니다. 내용 추출 중...");
        verifyFailure("자막 내용을 가져오지 못했습니다 (null 응답).", YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: 예상치 못한 RuntimeException으로 YoutubeApiException 변환 검증")
    void fetchSubs_catchesGenericRuntimeException_leadingToYoutubeApiException() throws TranscriptRetrievalException {
        when(mockApi.listTranscripts(testVideoId)).thenThrow(new IllegalStateException("완전히 예상 못한 런타임 오류"));

        subtitleService.fetchSubs(testJobId, testVideo);

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("유튜브 자막 목록 조회 중 예기치 못한 오류가 발생했습니다.", YoutubeApiException.class);
    }

    // "progress" 상태의 JobStatusDto를 검증하는 Matcher
    private static class ProgressJobStatusDtoMatcher implements ArgumentMatcher<JobStatusDto> {
        private final String expectedJobId;
        private final String expectedMessage;

        public ProgressJobStatusDtoMatcher(String jobId, String message) {
            this.expectedJobId = jobId;
            this.expectedMessage = message;
        }

        @Override
        public boolean matches(JobStatusDto argument) {
            if (argument == null) return false;
            return expectedJobId.equals(argument.jobId()) &&
                    JobStatusDto.JobStatus.PROCESSING == argument.status() &&
                    expectedMessage.equals(argument.result());
        }

        @Override
        public String toString() {
            return String.format("JobStatusDto with jobId=[%s], status=[PROCESSING], and result=[%s]",
                    expectedJobId, expectedMessage);
        }
    }

    // "error" 상태의 JobStatusDto를 검증하는 Matcher
    private static class ErrorJobStatusDtoMatcher implements ArgumentMatcher<JobStatusDto> {
        private final String expectedJobId;
        private final String expectedMessage;

        public ErrorJobStatusDtoMatcher(String jobId, String message) {
            this.expectedJobId = jobId;
            this.expectedMessage = message;
        }

        @Override
        public boolean matches(JobStatusDto argument) {
            if (argument == null) return false;
            return expectedJobId.equals(argument.jobId()) &&
                    JobStatusDto.JobStatus.FAILED == argument.status() &&
                    expectedMessage.equals(argument.result());
        }

        @Override
        public String toString() {
            return String.format("JobStatusDto with jobId=[%s], status=[FAILED], and result=[%s]",
                    expectedJobId, expectedMessage);
        }
    }

    // "complete" 상태의 JobStatusDto를 검증하는 Matcher
    private static class CompleteJobStatusDtoMatcher implements ArgumentMatcher<JobStatusDto> {
        private final String expectedJobId;
        // 결과 내용은 anyString()으로 처리할 것이므로 여기서는 메시지 비교 안함
        // 필요하다면 expectedMessage를 추가하여 비교 가능

        public CompleteJobStatusDtoMatcher(String jobId) {
            this.expectedJobId = jobId;
        }

        @Override
        public boolean matches(JobStatusDto argument) {
            if (argument == null) return false;
            // result 내용은 null이 아닌지만 확인
            return expectedJobId.equals(argument.jobId()) &&
                    JobStatusDto.JobStatus.COMPLETED == argument.status() &&
                    argument.result() != null;
        }

        @Override
        public String toString() {
            return String.format("JobStatusDto with jobId=[%s], status=[COMPLETED], and a non-null result",
                    expectedJobId);
        }
    }
}