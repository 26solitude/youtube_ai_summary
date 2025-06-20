# --- 1단계: 빌드 스테이지 ---
# 파이썬 3.11 공식 이미지를 'builder'라는 이름의 스테이지로 사용
FROM python:3.11-slim as builder

# yt-dlp 최신 버전을 설치
RUN pip install --upgrade yt-dlp

# --- 2단계: 최종 실행 스테이지 ---
# 기존과 동일한 자바 베이스 이미지 사용
FROM amazoncorretto:21-al2-jdk

# yum으로 curl만 설치 (python 설치 불필요)
RUN yum install -y curl

# 'builder' 스테이지에서 설치한 yt-dlp 실행 파일만 복사해옴
COPY --from=builder /usr/local/bin/yt-dlp /usr/local/bin/yt-dlp

# --- 이하 동일 ---
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]