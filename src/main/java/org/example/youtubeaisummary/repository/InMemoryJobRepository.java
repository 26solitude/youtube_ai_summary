package org.example.youtubeaisummary.repository;

import org.example.youtubeaisummary.dto.JobStatusDto;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryJobRepository {

    private final Map<String, JobStatusDto> jobs = new ConcurrentHashMap<>();

    // 새로운 작업 생성 (초기 상태: PENDING)
    public void createJob(String jobId) {
        jobs.put(jobId, new JobStatusDto(jobId, JobStatusDto.JobStatus.PENDING, null));
    }

    // 작업 상태 및 결과 조회
    public Optional<JobStatusDto> getJobStatus(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    // 작업 상태 및 결과 업데이트
    public void updateJob(String jobId, JobStatusDto.JobStatus status, String result) {
        jobs.put(jobId, new JobStatusDto(jobId, status, result));
    }
}
