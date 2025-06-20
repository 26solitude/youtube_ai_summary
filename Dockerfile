# 1. 베이스 이미지 선택: Amazon Linux 2
FROM amazoncorretto:21-al2-jdk

# 2. 외부 의존성 설치: yt-dlp 실행에 필요한 python과 curl을 설치합니다.
# RUN 명령어는 도커 이미지를 만드는 과정에서 실행되는 셸 명령어입니다.
RUN yum install -y python3 python3-pip curl

# 3. yt-dlp 설치: EC2 서버에서 했던 것과 동일한 명령어로 yt-dlp를 설치합니다.
RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
RUN chmod a+rx /usr/local/bin/yt-dlp
RUN yt-dlp -U

# 4. 작업 디렉토리 설정: 이미지 안에서 작업할 기본 폴더를 /app으로 지정합니다.
WORKDIR /app

# 5. 빌드된 JAR 파일 복사: Gradle 빌드 결과물인 JAR 파일을 이미지 안으로 복사하고 app.jar로 이름을 변경합니다.
# COPY build/libs/*.jar는 build/libs/ 폴더에 있는 어떤 이름의 jar 파일이든 복사하라는 의미입니다.
COPY build/libs/*.jar app.jar

# 6. 애플리케이션 실행: 컨테이너가 시작될 때 이 명령어를 실행합니다.
ENTRYPOINT ["java", "-jar", "app.jar"]