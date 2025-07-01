# 베이스 이미지를 표준 Debian 기반의 OpenJDK 21로 변경합니다.
FROM openjdk:21-slim

# Debian의 패키지 관리자인 apt를 사용하여 필요한 도구들을 설치합니다.
# apt-get update로 패키지 목록을 갱신하고,
# python3, pip, ffmpeg(자막 변환 시 필요)를 설치합니다.
# --no-install-recommends로 불필요한 패키지 설치를 막고,
# 마지막에 캐시 파일을 정리하여 최종 이미지 용량을 줄입니다.
RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 python3-pip ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# pip 자체를 최신 버전으로 업그레이드합니다.
RUN pip3 install --upgrade pip --break-system-packages

# yt-dlp를 최신 버전으로 설치합니다.
RUN pip3 install --upgrade yt-dlp --break-system-packages

# 설치된 버전을 로그에 출력하여 확인합니다.
RUN yt-dlp --version

# 애플리케이션 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 파일을 이미지로 복사
COPY build/libs/*.jar app.jar

# 컨테이너 시작 시 실행될 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]