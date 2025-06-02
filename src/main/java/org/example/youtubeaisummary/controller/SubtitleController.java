package org.example.youtubeaisummary.controller;

import org.example.youtubeaisummary.dto.JobResponseDto;
import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.repository.InMemoryJobRepository;
import org.example.youtubeaisummary.service.SseNotificationService;
import org.example.youtubeaisummary.service.SubtitleService;
import org.example.youtubeaisummary.vo.YoutubeVideo;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class SubtitleController {

    private final SubtitleService subtitleService;
    private final InMemoryJobRepository jobRepository;
    private final SseNotificationService sseNotificationService;

    public SubtitleController(SubtitleService subtitleService, InMemoryJobRepository jobRepository, SseNotificationService sseNotificationService) {
        this.subtitleService = subtitleService;
        this.jobRepository = jobRepository;
        this.sseNotificationService = sseNotificationService;
    }

    @PostMapping("/subtitles")
    public ResponseEntity<JobResponseDto> requestSubtitleProcessing(@RequestParam("url") YoutubeVideo video) {
        String jobId = UUID.randomUUID().toString();
        jobRepository.createJob(jobId);
        subtitleService.fetchSubs(jobId, video);
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