# 베이스 이미지로 Amazon Corretto 21 (Amazon Linux 2 기반)을 사용합니다.
FROM amazoncorretto:21-al2-jdk

# yum을 사용하여 python3와 pip를 설치합니다.
# -y 옵션으로 모든 프롬프트에 자동으로 'yes'를 응답합니다.
RUN yum update -y && yum install -y python3-pip

# pip를 사용하여 yt-dlp를 설치합니다.
RUN pip3 install --upgrade yt-dlp

# 애플리케이션 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 파일을 이미지로 복사
COPY build/libs/*.jar app.jar

# 컨테이너 시작 시 실행될 명령어
# prod 프로필을 활성화하여 애플리케이션을 실행합니다.
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]