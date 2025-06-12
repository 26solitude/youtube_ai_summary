package org.example.youtubeaisummary.service.subtitle;

import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.service.JobManager;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractSubtitleService implements SubtitleService {

    private JobManager jobManager;

    @Autowired
    public void setJobManager(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    /**
     * 작업 진행 상태를 업데이트하고 클라이언트에게 알림을 보냅니다.
     */
    protected void updateJobProgress(String jobId, JobStatusDto.JobStatus status, String message) {
        jobManager.updateJobProgress(jobId, status, message);
    }

    /**
     * 작업 실패를 처리하고 클라이언트에게 에러를 알립니다.
     */
    protected void handleFailure(String jobId, String message, Exception exception) {
        jobManager.failJob(jobId, message, exception);
    }
}
