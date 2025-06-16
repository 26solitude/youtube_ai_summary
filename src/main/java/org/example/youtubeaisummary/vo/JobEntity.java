package org.example.youtubeaisummary.vo;

import jakarta.persistence.*;
import org.example.youtubeaisummary.dto.JobStatusDto;

@Entity
@Table(name = "jobs")
public class JobEntity {

    @Id
    @Column(length = 64) // 길이를 적절히 지정
    private String jobId;

    @Enumerated(EnumType.STRING) // Enum 타입을 문자열로 저장
    @Column(length = 32)
    private JobStatusDto.JobStatus status;

    @Lob // 매우 긴 텍스트를 저장하기 위해
    @Column(columnDefinition = "LONGTEXT")
    private String result;

    // JPA를 위한 기본 생성자
    protected JobEntity() {
    }

    // 값 초기화를 위한 생성자
    public JobEntity(String jobId, JobStatusDto.JobStatus status, String result) {
        this.jobId = jobId;
        this.status = status;
        this.result = result;
    }

    // Getter
    public String getJobId() {
        return jobId;
    }

    public JobStatusDto.JobStatus getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }
}