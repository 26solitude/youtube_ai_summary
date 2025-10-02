package org.example.youtubeaisummary.repository;

import org.example.youtubeaisummary.dto.JobStatusDto;
import org.example.youtubeaisummary.vo.JobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("!test") // 테스트가 아닐 때 활성화
public class MySqlJobRepositoryImpl implements JobRepository {

    private static final Logger logger = LoggerFactory.getLogger(MySqlJobRepositoryImpl.class);
    private final JobEntityRepository jobEntityRepository; // JPA 리포지토리를 주입받음

    public MySqlJobRepositoryImpl(JobEntityRepository jobEntityRepository) {
        this.jobEntityRepository = jobEntityRepository;
    }

    @Override
    public boolean createJobIfAbsent(String jobId) {
        if (jobEntityRepository.existsById(jobId)) {
            return false;
        }
        JobEntity initialEntity = new JobEntity(jobId, JobStatusDto.JobStatus.PENDING, "작업 처리 대기 중...");

        try{
            jobEntityRepository.save(initialEntity);
            return true;
        }catch(DataIntegrityViolationException e){
            logger.warn("작업 생성 중 동시성 경쟁 발생. 작업 ID: {}. 다른 스레드에 의해 이미 생성되었습니다.", jobId);
            return false;
        }
    }

    @Override
    public Optional<JobStatusDto> getJobStatus(String jobId) {
        return jobEntityRepository.findById(jobId).map(this::toDto);
    }

    @Override
    public void updateJob(String jobId, JobStatusDto.JobStatus status, String message) {
        JobEntity entity = new JobEntity(jobId, status, message);
        jobEntityRepository.save(entity);
    }

    // Entity -> DTO 변환 헬퍼
    private JobStatusDto toDto(JobEntity entity) {
        return new JobStatusDto(entity.getJobId(), entity.getStatus(), entity.getResult());
    }
}