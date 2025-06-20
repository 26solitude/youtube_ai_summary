# --- 1단계: 빌드 스테이지 ---
FROM python:3.11-slim as builder
RUN pip install --upgrade yt-dlp

# --- 2단계: 최종 실행 스테이지 ---
FROM amazoncorretto:21-al2-jdk
RUN yum install -y curl

# pip로 설치될 때 일반적으로 사용되는 경로를 직접 명시합니다.
COPY --from=builder /usr/local/bin/yt-dlp /usr/local/bin/yt-dlp

WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]