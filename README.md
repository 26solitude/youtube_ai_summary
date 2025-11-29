
# 🎬 YouTube AI Summary Engine

> "영상을 읽다."
>
> YouTube 영상의 자막을 추출하고, LLM(OpenAI)의 동적 프롬프트 전략을 통해 구조화된 아티클로 변환하는 고성능 비동기 백엔드 서비스입니다.

## 📐 Architecture

### 1. System Overview

<img width="1270" height="921" alt="Image" src="https://github.com/user-attachments/assets/eb7ebbef-3fa6-4f01-ad8f-0ac30c654f13" />


시스템은 **트래픽 처리(Runtime)** 와 **운영 관리(Operations)** 의 역할이 명확히 분리된 구조를 가집니다.

- **Runtime Flow (실선):** 사용자의 요청을 처리하는 핵심 비즈니스 로직입니다. Nginx를 통해 **SSE(Server-Sent Events)** 스트림을 구독하여, 자막 추출부터 AI 요약까지의 전 과정을 실시간으로 전달받습니다.

- **Operations Flow (점선):** 시스템의 안정성을 뒷받침하는 백그라운드 프로세스입니다. GitHub Actions를 통한 **CI/CD 파이프라인**과, 유튜브 접근성을 보장하기 위한 **쿠키 자동 갱신(Cookie Harvesting)** 작업이 포함됩니다.


### 2. Detailed Execution Flow (Sequence)

복잡한 비동기 처리 과정을 **'작업 처리(Processing)'** 와 **'사용자 알림(Notification)'** 두 가지 관점으로 시각화했습니다.

#### **A. Async Job Processing (Business Logic)**

![Image](https://github.com/user-attachments/assets/e612b02b-4ad8-4a0b-9162-2168e81321d4)

요청이 들어오면 즉시 응답(`202 Accepted`)을 반환하고, 백그라운드에서 **I/O 작업(자막 추출)** 과 **AI 작업(요약)** 이 스레드 풀을 넘나들며 수행됩니다. 텍스트 길이에 따라 **Map-Reduce** 전략이 동적으로 적용되는 흐름을 확인할 수 있습니다.


#### **B. Real-time Notification (SSE Flow)**

![Image](https://github.com/user-attachments/assets/f141fcca-f543-4099-befb-8e553ad74a6a)

긴 작업 시간 동안 사용자가 이탈하지 않도록, 작업의 진행 상태(Progress)를 **실시간 이벤트 스트림**으로 전달하는 UX 중심의 흐름입니다.


## 🛠️ Tech Stack

|**Category**|**Technology**|**Description**|
|---|---|---|
|**Language**|**Java 21**|Record, Switch Expression 등 모던 자바 문법 활용|
|**Framework**|**Spring Boot 3.5**|최신 스냅샷 기반의 웹 프레임워크|
|**AI**|**Spring AI**|OpenAI 클라이언트 및 프롬프트 템플릿 관리|
|**Database**|**MySQL / H2**|Prod(MySQL)와 Local/Test(H2) 프로필 분리|
|**Infra**|**Docker & Actions**|`yt-dlp` 포함 커스텀 이미지 빌드 및 자동 배포|

## 🚀 Getting Started (Local Development)

이 프로젝트는 로컬 환경(`local` 프로필)에서 즉시 실행 가능하도록 구성되어 있습니다. 별도의 MySQL 설치 없이 **H2 인메모리 DB**로 동작합니다.

### Prerequisites

실행을 위해 아래 도구들이 시스템에 설치되어 있어야 합니다.

- **Java 21+**

- **OpenAI API Key**

- **yt-dlp** (최신 버전 권장)

- **ffmpeg** (`yt-dlp`의 오디오 처리 및 자막 변환을 위해 필수)


### 1. Clone & Configuration

프로젝트에는 로컬 설정을 위한 템플릿 파일이 포함되어 있습니다. 이를 복사하여 개인 설정을 적용합니다.

Bash

```
# 설정 파일 복사
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

`src/main/resources/application-local.properties` 파일을 열어 아래 항목을 입력합니다.

Properties

```
# [필수] 설치된 yt-dlp의 절대 경로 입력
# Windows 예시: C:/Tools/yt-dlp.exe
# macOS/Linux 예시: /usr/local/bin/yt-dlp
app.ytdlp.path=/your/path/to/yt-dlp

# [필수] OpenAI API 키 입력
spring.ai.openai.api-key=sk-YOUR-OPENAI-API-KEY
```

> **Note:** `application-local.properties`는 gitignore에 등록되어 있어 커밋되지 않습니다.

### 2. Run Application

Gradle Wrapper를 사용하여 애플리케이션을 실행합니다.

Bash

```
./gradlew bootRun
```

## 🔌 API Documentation

서버가 실행되면(`localhost:8080`) 아래 엔드포인트를 통해 테스트할 수 있습니다.

|**Method**|**Endpoint**|**Description**|
|---|---|---|
|`POST`|`/api/jobs/subtitles?url={youtube_url}`|영상 요약 요청 (Async)|
|`GET`|`/api/jobs/subtitles/stream/{jobId}`|실시간 진행 상태 구독 (SSE)|
|`GET`|`/api/jobs/subtitles/status/{jobId}`|작업 상태 단건 조회|

### Request Example

Bash

```
# 1. 요약 요청 (비동기)
curl -X POST "http://localhost:8080/api/jobs/subtitles?url=https://youtu.be/VIDEO_ID"

# 2. 실시간 상태 확인 (반환된 jobId 사용)
# 웹 브라우저에서 해당 URL을 입력하면 실시간으로 로그가 쌓이는 것을 볼 수 있습니다.
curl -N "http://localhost:8080/api/jobs/subtitles/stream/{jobId}"
```

---

### License

This project is licensed under the MIT License.
