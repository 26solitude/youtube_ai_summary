package org.example.youtubeaisummary.controller;

import org.example.youtubeaisummary.dto.JobResponseDto;
import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.repository.JobRepository;
import org.example.youtubeaisummary.service.OrchestrationService;
import org.example.youtubeaisummary.service.SseNotificationService;
import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final OrchestrationService orchestrationService;
    private final JobRepository jobRepository;
    private final SseNotificationService sseNotificationService;

    public JobController(OrchestrationService orchestrationService, JobRepository jobRepository, SseNotificationService sseNotificationService) {
        this.orchestrationService = orchestrationService;
        this.jobRepository = jobRepository;
        this.sseNotificationService = sseNotificationService;
    }

    @PostMapping("/subtitles")
    public ResponseEntity<?> requestSubtitleProcessing(@RequestParam("url") YoutubeVideo video) {
        String jobId = video.getVideoId();

        // 1. 먼저 작업 생성을 시도 (Optimistic Approach)
        if (jobRepository.createJobIfAbsent(jobId)) {
            // 성공: 새로운 작업이므로 즉시 서비스 호출
            orchestrationService.processYoutubeVideo(jobId, video);
        } else {
            // 실패: 이미 작업이 존재함. 상태를 한 번만 조회하여 분기 처리
            Optional<JobStatusDto> jobOpt = jobRepository.getJobStatus(jobId);

            if (jobOpt.isPresent()) {
                JobStatusDto statusDto = jobOpt.get();
                if (statusDto.status() == JobStatusDto.JobStatus.COMPLETED) {
                    // 완료된 작업: 즉시 결과 반환
                    return ResponseEntity.ok(statusDto);
                }
                if (statusDto.status() == JobStatusDto.JobStatus.FAILED) {
                    // 실패한 작업: 재시도 로직 실행
                    orchestrationService.processYoutubeVideo(jobId, video);
                }
                // PENDING 또는 다른 진행중인 상태는 아무것도 하지 않고 아래의 ACCEPTED를 반환
            }
        }

        // 신규 요청이든, 진행 중인 요청이든, 재시도 요청이든 모두 ACCEPTED 응답
        return new ResponseEntity<>(new JobResponseDto(jobId), HttpStatus.ACCEPTED);

    }

    @GetMapping(value = "/subtitles/stream/{jobId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobUpdates(@PathVariable String jobId) {
        if (!jobRepository.getJobStatus(jobId).isPresent()) {
            SseEmitter deadEmitter = new SseEmitter();
            deadEmitter.completeWithError(new IllegalArgumentException("Invalid Job ID: " + jobId));
            return deadEmitter;
        }
        return sseNotificationService.subscribe(jobId);
    }

    @GetMapping("/subtitles/status/{jobId}")
    public ResponseEntity<JobStatusDto> getJobStatus(@PathVariable String jobId) {
        return jobRepository.getJobStatus(jobId)
                .map(jobStatus -> new ResponseEntity<>(jobStatus, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}