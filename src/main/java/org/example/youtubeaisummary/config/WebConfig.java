package org.example.youtubeaisummary.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // '/api/'로 시작하는 모든 경로에 CORS 정책 적용
                .allowedOrigins("https://www.youtube.com") // 유튜브 페이지 요청을 허용
                .allowedMethods("GET", "POST", "OPTIONS") // 허용할 HTTP 메서드
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(true) // 자격 증명(쿠키 등)을 포함한 요청 허용
                .maxAge(3600); // pre-flight 요청의 결과를 캐시할 시간(초)
    }
}