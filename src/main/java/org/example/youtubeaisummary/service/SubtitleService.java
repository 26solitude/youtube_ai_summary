package org.example.youtubeaisummary.service;


import org.example.youtubeaisummary.vo.YoutubeVideo;

import java.util.concurrent.CompletableFuture;

public interface SubtitleService {
    /**
     * 주어진 YouTube 영상의 자막을 비동기적으로 추출합니다.
     *
     * @param jobId 처리 과정을 추적하기 위한 작업 ID
     * @param video 자막을 추출할 YouTube 영상 정보
     * @return 추출된 자막 텍스트를 담은 CompletableFuture
     */
    CompletableFuture<String> fetchSubs(String jobId, YoutubeVideo video);
}