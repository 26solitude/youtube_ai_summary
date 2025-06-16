package org.example.youtubeaisummary.repository;

import org.example.youtubeaisummary.vo.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobEntityRepository extends JpaRepository<JobEntity, String> {
}