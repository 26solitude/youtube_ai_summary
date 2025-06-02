package org.example.youtubeaisummary.service;


import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.repository.InMemoryJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseNotificationService {
    public static final Logger logger = LoggerFactory.getLogger(SseNotificationService.class);
    public static final Long SSE_EMITTER_TIMEOUT = (long) 5 * 60 * 1000;  // 5분
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final InMemoryJobRepository jobRepository;

    public SseNotificationService(InMemoryJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public SseEmitter subscribe(String jobId) {
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT);
        emitters.put(jobId, emitter);

        emitter.onCompletion(() -> {
            logger.info("SSE Emitter completed for job: {}", jobId);
            emitters.remove(jobId);
        });
        emitter.onTimeout(() -> {
            logger.info("SSE Emitter timed out for job: {}", jobId);
            emitters.remove(jobId);
            emitter.complete();
        });
        emitter.onError(e -> {
            logger.error("SSE Emitter error for job: {}: {}", jobId, e.getMessage());
            emitters.remove(jobId);
        });

        Optional<JobStatusDto> currentJobOpt = jobRepository.getJobStatus(jobId);

        if (currentJobOpt.isPresent()) {
            JobStatusDto currentStatus = currentJobOpt.get();
            String eventName = determineEventNameFromStatus(currentStatus.status());
            sendEvent(jobId, eventName, currentStatus); // 현재 상태를 첫 이벤트로 즉시 전송

            if (currentStatus.status() == JobStatusDto.JobStatus.COMPLETED) {
                logger.info("Job {} already completed. Sending final status and completing emitter from subscribe.", jobId);
                completeStream(jobId);
            } else if (currentStatus.status() == JobStatusDto.JobStatus.FAILED) {
                logger.info("Job {} already failed. Sending final status and erroring emitter from subscribe.", jobId);
                errorStream(jobId, new RuntimeException(currentStatus.result() != null ? currentStatus.result() : "Job failed with no specific message"));
            }
        } else {
            logger.warn("JobId {} not found in repository during SSE subscription. Sending PENDING.", jobId);
            sendEvent(jobId, "pending", new JobStatusDto(jobId, JobStatusDto.JobStatus.PENDING, "Awaiting job processing..."));
        }
        return emitter;
    }

    private String determineEventNameFromStatus(JobStatusDto.JobStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case PROCESSING -> "progress";
            case COMPLETED -> "complete";
            case FAILED -> "error";
            default -> "statusUpdate";
        };
    }

    public void sendEvent(String jobId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name(eventName)
                        .data(data)
                        .reconnectTime(10_000L));
                logger.debug("Sent SSE event for jobId: {}, name: {}, data: {}", jobId, eventName, data);
            } catch (IOException e) {
                logger.error("Error sending SSE event for jobId: {}: {}", jobId, e.getMessage());
                emitters.remove(jobId);
                emitter.completeWithError(e);
            }
        } else {
            logger.warn("No SseEmitter found for jobId: {}. Event '{}' not sent.", jobId, eventName);
        }
    }

    public void completeStream(String jobId) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) emitter.complete();
    }

    public void errorStream(String jobId, Throwable throwable) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) emitter.completeWithError(throwable);
    }
}
