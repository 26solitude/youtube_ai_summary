package org.example.youtubeaisummary.service;

import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobManager {
    private static final Logger logger = LoggerFactory.getLogger(JobManager.class);
    private final JobRepository jobRepository;
    private final SseNotificationService sseNotificationService;

    public JobManager(JobRepository jobRepository, SseNotificationService sseNotificationService) {
        this.jobRepository = jobRepository;
        this.sseNotificationService = sseNotificationService;
    }

    /**
     * 작업을 성공적으로 완료 처리합니다.
     */
    public void completeJob(String jobId, String result) {
        jobRepository.updateJob(jobId, JobStatusDto.JobStatus.COMPLETED, result);
        sseNotificationService.notifyJobStatus(new JobStatusDto(jobId, JobStatusDto.JobStatus.COMPLETED, result));
        sseNotificationService.completeStream(jobId);
        logger.info("작업 ID: {} - 성공적으로 완료되었습니다.", jobId);
    }

    /**
     * 작업을 실패 처리합니다.
     */
    public void failJob(String jobId, String errorMessage, Exception e) {
        logger.error("작업 ID: {} - 실패: {}", jobId, errorMessage, e);
        jobRepository.updateJob(jobId, JobStatusDto.JobStatus.FAILED, errorMessage);
        sseNotificationService.notifyJobStatus(new JobStatusDto(jobId, JobStatusDto.JobStatus.FAILED, errorMessage));
        sseNotificationService.errorStream(jobId, e);
    }

    /**
     * 작업 진행 상태를 업데이트하고 클라이언트에게 알림을 보냅니다. (추가된 메서드)
     */
    public void updateJobProgress(String jobId, JobStatusDto.JobStatus status, String message) {
        jobRepository.updateJob(jobId, status, message);
        sseNotificationService.notifyJobStatus(new JobStatusDto(jobId, status, message));
        logger.info("작업 ID: {} - 상태 업데이트: {} - {}", jobId, status, message);
    }
}