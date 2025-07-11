package org.example.youtubeaisummary.dto;

public record JobStatusDto(String jobId, JobStatus status, String result) {
    public enum JobStatus {
        PENDING,
        SUBTITLE_EXTRACTING,
        SUBTITLE_EXTRACTION_COMPLETED,
        AI_SUMMARIZING_PARTIAL,
        AI_SUMMARIZING_FINAL,
        COMPLETED,
        FAILED
    }
}
