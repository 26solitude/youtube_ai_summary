package org.example.youtubeaisummary.repository;


import org.example.youtubeaisummary.dto.JobStatusDto;

import java.util.Optional;

public interface JobRepository {
    boolean createJobIfAbsent(String jobId);

    Optional<JobStatusDto> getJobStatus(String jobId);

    void updateJob(String jobId, JobStatusDto.JobStatus status, String message);
}
