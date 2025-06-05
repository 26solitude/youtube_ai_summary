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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
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

    // --- 특정 메시지를 가진 "progress" 이벤트 검증 헬퍼 (기존과 동일) ---
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
    void fetchSubs_successPath() throws Exception { // throws Exception 추가
        // Arrange
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        Transcript mockTranscript = mock(Transcript.class);
        TranscriptContent mockTranscriptContent = mock(TranscriptContent.class);

        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.of(mockTranscript).spliterator());
        when(mockTranscript.isGenerated()).thenReturn(true);
        when(mockTranscript.fetch()).thenReturn(mockTranscriptContent);
        // TextFormatter.format은 static method라 직접 모킹하기 어려움. 현재 anyString() 검증 유지.

        // Act
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);

        // Assert: CompletableFuture가 완료될 때까지 기다림
        String actualResult = future.get(); // 예외 발생 시 테스트 실패
        assertNotNull(actualResult); // 결과가 null이 아님을 확인 (선택 사항)

        // 이제 Mockito 검증 수행
        InOrder inOrder = inOrder(mockJobRepository, mockSseNotificationService);

        inOrder.verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 추출을 시작합니다..."));
        inOrder.verify(mockSseNotificationService).sendEvent(
                eq(testJobId),
                eq("progress"),
                argThat(new ProgressJobStatusDtoMatcher(testJobId, "자막 추출을 시작합니다..."))
        );
        inOrder.verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 정보를 가져왔습니다. 내용 추출 중..."));
        inOrder.verify(mockSseNotificationService).sendEvent(
                eq(testJobId),
                eq("progress"),
                argThat(new ProgressJobStatusDtoMatcher(testJobId, "자막 정보를 가져왔습니다. 내용 추출 중..."))
        );
        inOrder.verify(mockJobRepository).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.COMPLETED), anyString());
        inOrder.verify(mockSseNotificationService).sendEvent(
                eq(testJobId),
                eq("complete"),
                argThat(new CompleteJobStatusDtoMatcher(testJobId))
        );
        inOrder.verify(mockSseNotificationService).completeStream(eq(testJobId));

        verify(mockSseNotificationService, never()).sendEvent(eq(testJobId), eq("error"), any());
        verify(mockSseNotificationService, never()).errorStream(eq(testJobId), any());
    }

    private void test_general_failure_path(Class<? extends Exception> expectedCauseException, String expectedFailureMessage, Class<? extends Exception> expectedExceptionClassForSseInVerifyFailure) {
        // Act
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);

        // Assert: CompletableFuture가 예외로 완료되는지 확인
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);

        Throwable cause = executionException.getCause();
        assertTrue(expectedCauseException.isInstance(cause), "Expected cause to be " + expectedCauseException.getSimpleName());
        // 메시지 검증은 SubtitleService에서 던지는 예외의 메시지를 따라야 함.
        // 예를 들어 NoSubtitlesFoundException 이나 YoutubeApiException의 메시지.
        // verifyFailure에서 이미 메시지를 검증하므로 여기서는 타입만 확인해도 충분할 수 있음.
        // assertEquals(expectedFailureMessage, cause.getMessage()); // 필요시 메시지 직접 검증

        // Mockito 검증 (예외 상황에서 호출되어야 하는 메서드들)
        verifySpecificProgressEvent("자막 추출을 시작합니다..."); // 이 부분은 예외 발생 경로에 따라 호출되지 않을 수 있음. 각 테스트에서 개별 확인 필요.
        // 하지만 현재 SubtitleService 구조상 첫 updateJobProgress는 대부분 호출됨.
        verifyFailure(expectedFailureMessage, expectedExceptionClassForSseInVerifyFailure);
    }


    @Test
    @DisplayName("오류: listTranscripts 시 TranscriptRetrievalException (video unavailable)")
    void fetchSubs_listTranscripts_throwsTRE_videoUnavailable() throws TranscriptRetrievalException {
        // Arrange
        TranscriptRetrievalException treException = new TranscriptRetrievalException("Simulated video unavailable or not found");
        when(mockApi.listTranscripts(testVideoId)).thenThrow(treException);

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof NoSubtitlesFoundException);
        assertEquals("영상을 찾을 수 없거나 접근할 수 없습니다.", cause.getMessage());

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("영상을 찾을 수 없거나 접근할 수 없습니다.", NoSubtitlesFoundException.class);
    }

    @Test
    @DisplayName("오류: listTranscripts 시 TranscriptRetrievalException (subtitles disabled)")
    void fetchSubs_listTranscripts_throwsTRE_subtitlesDisabled() throws TranscriptRetrievalException {
        // Arrange
        TranscriptRetrievalException treException = new TranscriptRetrievalException("Simulated subtitles disabled for this video");
        when(mockApi.listTranscripts(testVideoId)).thenThrow(treException);

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof NoSubtitlesFoundException);
        assertEquals("이 영상에는 자막 기능이 비활성화되어 있습니다.", cause.getMessage());

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure("이 영상에는 자막 기능이 비활성화되어 있습니다.", NoSubtitlesFoundException.class);
    }

    @Test
    @DisplayName("오류: listTranscripts 시 TranscriptRetrievalException (기타 원인)")
    void fetchSubs_listTranscripts_throwsTRE_otherReason() throws TranscriptRetrievalException {
        // Arrange
        TranscriptRetrievalException treException = new TranscriptRetrievalException("Some other transcript retrieval error");
        when(mockApi.listTranscripts(testVideoId)).thenThrow(treException);
        String expectedMsg = "유튜브 자막 목록 조회 중 오류가 발생했습니다: " + treException.getMessage();

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof YoutubeApiException);
        assertEquals(expectedMsg, cause.getMessage());

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure(expectedMsg, YoutubeApiException.class);
    }


    @Test
    @DisplayName("오류: listTranscripts 시 RuntimeException 발생")
    void fetchSubs_listTranscripts_throwsRuntimeException() throws TranscriptRetrievalException {
        // Arrange
        RuntimeException runtimeException = new RuntimeException("Simulated runtime error during listTranscripts");
        when(mockApi.listTranscripts(testVideoId)).thenThrow(runtimeException);
        String expectedMsg = "유튜브 자막 목록 조회 중 예기치 못한 오류가 발생했습니다.";

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof YoutubeApiException);
        assertEquals(expectedMsg, cause.getMessage());
        // assertTrue(cause.getCause() instanceof RuntimeException); // 필요시 원인 예외 추가 검증

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure(expectedMsg, YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: listTranscripts가 null 반환")
    void fetchSubs_listTranscripts_returnsNull() throws TranscriptRetrievalException {
        // Arrange
        when(mockApi.listTranscripts(testVideoId)).thenReturn(null);
        String expectedMsg = "유튜브로부터 자막 목록을 가져오지 못했습니다 (null 응답).";

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof YoutubeApiException);
        assertEquals(expectedMsg, cause.getMessage());

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure(expectedMsg, YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: 자동 생성 자막 없음")
    void fetchSubs_noAutoGeneratedTranscript() throws TranscriptRetrievalException {
        // Arrange
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.<Transcript>empty().spliterator());
        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        String expectedMsg = "이 영상에는 자동 생성된 자막이 없습니다.";

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof NoSubtitlesFoundException);
        assertEquals(expectedMsg, cause.getMessage());

        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        verifyFailure(expectedMsg, NoSubtitlesFoundException.class);
    }

    @Test
    @DisplayName("오류: fetch() 시 TranscriptRetrievalException 발생")
    void fetchSubs_fetchContent_throwsTRE() throws TranscriptRetrievalException {
        // Arrange
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        Transcript mockTranscript = mock(Transcript.class);
        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.of(mockTranscript).spliterator());
        when(mockTranscript.isGenerated()).thenReturn(true);
        TranscriptRetrievalException treException = new TranscriptRetrievalException("Simulated fetch content error");
        when(mockTranscript.fetch()).thenThrow(treException);
        String expectedMsg = "자막 내용을 가져오는 중 오류가 발생했습니다: " + treException.getMessage();

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof YoutubeApiException);
        assertEquals(expectedMsg, cause.getMessage());

        // 이 경우는 두 번째 progress event도 확인해야 함
        verify(mockJobRepository, times(1)).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 추출을 시작합니다..."));
        verify(mockJobRepository, times(1)).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 정보를 가져왔습니다. 내용 추출 중..."));
        // verifySpecificProgressEvent를 직접 호출하기보다는 개별 verify로 처리하거나, InOrder 사용 고려
        verifyFailure(expectedMsg, YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: fetch() 시 RuntimeException 발생")
    void fetchSubs_fetchContent_throwsRuntimeException() throws TranscriptRetrievalException {
        // Arrange
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        Transcript mockTranscript = mock(Transcript.class);
        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.of(mockTranscript).spliterator());
        when(mockTranscript.isGenerated()).thenReturn(true);
        RuntimeException runtimeException = new RuntimeException("Simulated runtime error during fetch");
        when(mockTranscript.fetch()).thenThrow(runtimeException);
        String expectedMsg = "자막 내용을 가져오는 중 예기치 못한 오류가 발생했습니다.";

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof YoutubeApiException);
        assertEquals(expectedMsg, cause.getMessage());

        verify(mockJobRepository, times(1)).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 추출을 시작합니다..."));
        verify(mockJobRepository, times(1)).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 정보를 가져왔습니다. 내용 추출 중..."));
        verifyFailure(expectedMsg, YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: fetch()가 null 반환")
    void fetchSubs_fetchContent_returnsNull() throws TranscriptRetrievalException {
        // Arrange
        TranscriptList mockTranscriptList = mock(TranscriptList.class);
        Transcript mockTranscript = mock(Transcript.class);
        when(mockApi.listTranscripts(testVideoId)).thenReturn(mockTranscriptList);
        when(mockTranscriptList.spliterator()).thenReturn(Stream.of(mockTranscript).spliterator());
        when(mockTranscript.isGenerated()).thenReturn(true);
        when(mockTranscript.fetch()).thenReturn(null);
        String expectedMsg = "자막 내용을 가져오지 못했습니다 (null 응답).";

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause();
        assertTrue(cause instanceof YoutubeApiException);
        assertEquals(expectedMsg, cause.getMessage());

        verify(mockJobRepository, times(1)).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 추출을 시작합니다..."));
        verify(mockJobRepository, times(1)).updateJob(eq(testJobId), eq(JobStatusDto.JobStatus.PROCESSING), eq("자막 정보를 가져왔습니다. 내용 추출 중..."));
        verifyFailure(expectedMsg, YoutubeApiException.class);
    }

    @Test
    @DisplayName("오류: 예상치 못한 RuntimeException으로 YoutubeApiException 변환 검증")
    void fetchSubs_catchesGenericRuntimeException_leadingToYoutubeApiException() throws TranscriptRetrievalException {
        // Arrange
        when(mockApi.listTranscripts(testVideoId)).thenThrow(new IllegalStateException("완전히 예상 못한 런타임 오류"));
        String expectedMsg = "유튜브 자막 목록 조회 중 예기치 못한 오류가 발생했습니다."; // SubtitleService의 catch(RuntimeException) 블록 메시지

        // Act & Assert
        CompletableFuture<String> future = subtitleService.fetchSubs(testJobId, testVideo);
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        Throwable cause = executionException.getCause(); // YoutubeApiException
        assertTrue(cause instanceof YoutubeApiException);
        assertEquals(expectedMsg, cause.getMessage());
        assertTrue(cause.getCause() instanceof IllegalStateException); // 원래 예외 확인
        assertEquals("완전히 예상 못한 런타임 오류", cause.getCause().getMessage());


        verifySpecificProgressEvent("자막 추출을 시작합니다...");
        // verifyFailure의 메시지는 SubtitleService의 catch(RuntimeException) 블록에서 생성된 YoutubeApiException의 메시지를 사용해야 합니다.
        verifyFailure(expectedMsg, YoutubeApiException.class);
    }


    // Matcher 클래스들은 기존과 동일하게 유지
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

    private static class CompleteJobStatusDtoMatcher implements ArgumentMatcher<JobStatusDto> {
        private final String expectedJobId;

        public CompleteJobStatusDtoMatcher(String jobId) {
            this.expectedJobId = jobId;
        }

        @Override
        public boolean matches(JobStatusDto argument) {
            if (argument == null) return false;
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