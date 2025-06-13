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
        Optional<JobStatusDto> jobOpt = jobRepository.getJobStatus(jobId);

        // 완료된 작업: 즉시 결과 반환
        if (jobOpt.isPresent() && jobOpt.get().status() == JobStatusDto.JobStatus.COMPLETED) {
            return ResponseEntity.ok(jobOpt.get());
        }

        // 실패한 작업: 재시도 로직 실행
        if (jobOpt.isPresent() && jobOpt.get().status() == JobStatusDto.JobStatus.FAILED) {
            // 실패한 경우, 중복 실행 걱정 없이 바로 재시도
            orchestrationService.processYoutubeVideo(jobId, video);
            return new ResponseEntity<>(new JobResponseDto(jobId), HttpStatus.ACCEPTED);
        }

        // 신규 작업: 경쟁 상태를 방지하며 단 한번만 서비스 호출
        if (jobRepository.createJobIfAbsent(jobId)) {
            // createJobIfAbsent가 true를 반환할 때, 즉 여러 스레드 중
            // 최초로 작업을 등록하는 데 성공한 단 하나의 스레드만 이 블록을 실행
            orchestrationService.processYoutubeVideo(jobId, video);
        }

        // 진행 중이거나, 방금 신규/실패 처리가 시작된 모든 경우
        // 클라이언트에게는 작업이 정상적으로 접수되었음을 알립니다.
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