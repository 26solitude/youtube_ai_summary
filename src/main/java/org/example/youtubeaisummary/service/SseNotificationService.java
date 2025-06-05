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
            logger.info("작업 ID: {}에 대한 SSE Emitter가 완료되었습니다.", jobId); // 한글로 변경
            emitters.remove(jobId);
        });
        emitter.onTimeout(() -> {
            logger.info("작업 ID: {}에 대한 SSE Emitter 시간이 초과되었습니다.", jobId); // 한글로 변경
            emitters.remove(jobId);
            emitter.complete();
        });
        emitter.onError(e -> {
            logger.error("작업 ID: {}에 대한 SSE Emitter 오류: {}", jobId, e.getMessage(), e); // 한글로 변경, 예외 객체 로깅 추가
            emitters.remove(jobId);
        });

        Optional<JobStatusDto> currentJobOpt = jobRepository.getJobStatus(jobId);

        if (currentJobOpt.isPresent()) {
            JobStatusDto currentStatus = currentJobOpt.get();
            String eventName = determineEventNameFromStatus(currentStatus.status());
            sendEvent(jobId, eventName, currentStatus); // 현재 상태를 첫 이벤트로 즉시 전송

            if (currentStatus.status() == JobStatusDto.JobStatus.COMPLETED) {
                logger.info("작업 ID {}는 이미 완료되었습니다. 구독 시 최종 상태를 전송하고 Emitter를 종료합니다.", jobId); // 한글로 변경
                completeStream(jobId);
            } else if (currentStatus.status() == JobStatusDto.JobStatus.FAILED) {
                logger.info("작업 ID {}는 이미 실패했습니다. 구독 시 최종 상태를 전송하고 Emitter를 오류 상태로 종료합니다.", jobId); // 한글로 변경
                errorStream(jobId, new RuntimeException(currentStatus.result() != null ? currentStatus.result() : "작업 실패 (상세 메시지 없음)")); // 한글로 변경
            }
        } else {
            logger.warn("SSE 구독 중 JobRepository에서 작업 ID {}를 찾을 수 없습니다. PENDING 상태를 전송합니다.", jobId); // 한글로 변경
            sendEvent(jobId, "pending", new JobStatusDto(jobId, JobStatusDto.JobStatus.PENDING, "작업 처리 대기 중...")); // 한글로 변경
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
                logger.debug("SSE 이벤트 전송 완료. 작업 ID: {}, 이벤트명: {}, 데이터: {}", jobId, eventName, data); // 한글로 변경
            } catch (IOException e) {
                logger.error("SSE 이벤트 전송 중 오류 발생. 작업 ID: {}: {}", jobId, e.getMessage(), e); // 한글로 변경, 예외 객체 로깅 추가
                emitters.remove(jobId);
                emitter.completeWithError(e);
            }
        } else {
            logger.warn("작업 ID: {}에 해당하는 SseEmitter를 찾을 수 없습니다. 이벤트 '{}'가 전송되지 않았습니다.", jobId, eventName); // 한글로 변경
        }
    }

    public void completeStream(String jobId) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    public void errorStream(String jobId, Throwable throwable) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            emitter.completeWithError(throwable);
        }
    }
}