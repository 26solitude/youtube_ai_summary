package org.example.youtubeaisummary.dto;

public record JobStatusDto(String jobId, JobStatus status, String result) {
    public enum JobStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
