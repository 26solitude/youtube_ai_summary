package org.example.youtubeaisummary.service;

import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.repository.InMemoryJobRepository;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractSubtitleService implements SubtitleService {

    protected InMemoryJobRepository jobRepository;
    protected SseNotificationService sseNotificationService;

    @Autowired
    public void setDependencies(InMemoryJobRepository jobRepository, SseNotificationService sseNotificationService) {
        this.jobRepository = jobRepository;
        this.sseNotificationService = sseNotificationService;
    }

    /**
     * 작업 진행 상태를 업데이트하고 클라이언트에게 알림을 보냅니다.
     */
    protected void updateJobProgress(String jobId, JobStatusDto.JobStatus status, String message) {
        jobRepository.updateJob(jobId, status, message);
        sseNotificationService.notifyJobStatus(new JobStatusDto(jobId, status, message));
    }

    /**
     * 작업 실패를 처리하고 클라이언트에게 에러를 알립니다.
     */
    protected void handleFailure(String jobId, String message, Exception exception) {
        updateJobProgress(jobId, JobStatusDto.JobStatus.FAILED, message);
        sseNotificationService.errorStream(jobId, exception);
    }
}
