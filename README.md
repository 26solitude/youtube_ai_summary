# YouTube AI Summary

YouTube 영상의 자막을 추출하고 AI를 통해 가독성 높은 문어체 아티클 형식으로 재구성하는 비동기 처리 백엔드 서비스입니다. 사용자는 작업 요청 후 즉시 응답을 받고, **Server-Sent Events(SSE)**를 통해 처리 과정을 실시간으로 확인할 수 있습니다.

## ✨ 주요 특징

- **🎯 작업 특성을 고려한 비동기 처리 최적화**: I/O 바운드 작업(자막 추출, 파일 I/O)과 네트워크/CPU 바운드 작업(AI API 호출)의 특성에 맞춰 스레드 풀을 `ioTaskExecutor`와 `aiTaskExecutor`로 명확하게 분리했습니다. 이 설계는 특정 유형의 작업 병목이 다른 작업에 영향을 미치는 것을 방지하여 시스템 전체의 처리량과 안정성을 극대화합니다. `CompletableFuture`를 활용하여 전체 비동기 흐름을 파이프라인으로 구성하고, 각 단계를 적절한 스레드 풀에 위임하여 시스템 자원을 효율적으로 사용합니다.

- **🔧 전략 패턴을 활용한 유연한 자막 추출**: 자막 추출 로직을 `SubtitleService` 인터페이스로 추상화하고, `YtDlpSubtitleService`와 `YoutubeApiSubtitleService`라는 두 가지 구현체를 제공합니다. 이를 통해 `application.properties`의 `app.subtitle.provider` 설정값 변경만으로 코드 수정 없이 실제 동작하는 자막 추출 전략을 손쉽게 교체할 수 있습니다.

- **🧠 동적 AI 처리 전략 (Map-Reduce)**: Spring AI를 활용하여 텍스트를 재구성하며, 입력된 자막의 길이에 따라 처리 전략을 동적으로 선택합니다. 짧은 텍스트는 단일 요청으로 처리하고, 긴 텍스트는 토큰 제한과 비용 효율성을 고려하여 최적의 청크 단위로 분할 처리 후 병합하는 **Map-Reduce 방식**을 적용합니다. 이 때, 청크가 너무 많아지지 않도록 최대 청크 수를 제한하는 로직도 포함되어 있습니다.

- **🛡️ 멱등성(Idempotent)을 보장하는 API 설계**: 작업 요청 API는 멱등성을 고려하여 설계되었습니다. 동일한 영상에 대해 작업이 이미 완료되었다면 즉시 결과를 반환하고, 실패했다면 재시도를 허용하며, 진행 중이거나 신규 요청일 경우에만 작업을 시작합니다. 이는 불필요한 중복 실행을 방지하고 API 안정성을 높입니다.

- **🧩 책임 분리를 통한 높은 응집도**: 각 컴포넌트의 역할과 책임을 명확히 분리하여 코드의 응집도와 유지보수성을 향상시켰습니다.

    - `OrchestrationService`: 자막 추출과 AI 요약 등 전체 작업 흐름을 조율합니다.

    - `JobManager`: 작업의 상태(성공, 실패, 진행)를 관리하고 데이터베이스에 영속화하며, SSE 알림을 트리거하는 책임을 전담합니다.

    - `SseNotificationService`: 클라이언트와의 SSE 연결을 수립하고, 작업의 각 단계마다 실시간으로 상태를 전송합니다.

- **💾 프로필 기반 데이터 영속성 관리**: 작업 상태를 `JobEntity`로 정의하고 JPA를 통해 RDBMS에 영속적으로 저장합니다. Spring Profile을 활용하여, 운영(`prod`) 환경에서는 `MySqlJobRepositoryImpl`을, 로컬 및 테스트 환경에서는 `InMemoryJobRepository`를 사용하도록 구성하여 개발 및 테스트의 편의성을 확보했습니다.

- **🚀 Docker 기반의 자동화된 CI/CD 파이프라인**: `master` 브랜치 Push 시 **GitHub Actions** 워크플로우가 자동으로 실행됩니다. 이 파이프라인은 Gradle 빌드, Docker 이미지 생성 및 Docker Hub 푸시, EC2 서버 접속 및 컨테이너 재배포까지의 전 과정을 자동화하여 안정적이고 빠른 배포를 보장합니다.


## 📐 아키텍처 및 동작 방식

본 시스템은 요청을 즉시 수락하고 백그라운드에서 비동기적으로 작업을 처리하는 이벤트 기반 아키텍처를 따릅니다. 작업의 전체 생명주기와 시스템 컴포넌트 간의 상호작용은 아래 다이어그램을 통해 상세히 이해할 수 있습니다.

### 작업 상태 흐름 (State Diagram)
![](https://i.imgur.com/1xDmp8r.png)
하나의 작업(Job)이 생성되어 완료되거나 실패하기까지의 모든 상태 변화 경로를 보여줍니다. 작업 상태는 `JobStatus` Enum으로 관리됩니다.

### 시스템 상호작용 (Sequence Diagram)
![](https://i.imgur.com/H4dzHZp.png)
사용자의 요청부터 최종 결과를 받기까지의 과정을 시간 순서대로 보여줍니다.

## 🚀 시작하기

### 요구사항

- Java (JDK) 21

- Gradle 8.4+

- `yt-dlp`: `ytDlp` 전략을 사용할 경우, 시스템에 설치되어 있고 PATH에 등록되어 있어야 함.

- OpenAI API Key

- MySQL (운영 환경)

- Docker


### 실행 방법

1. **프로젝트 클론**

   Bash

    ```
    git clone https://github.com/26solitude/youtube-ai-summary.git
    cd youtube-ai-summary
    ```

2. **설정 파일 생성 및 수정** `src/main/resources/` 경로에 `application-secret.properties` 파일을 생성하고 자신의 환경에 맞게 DB 및 API 키 정보를 입력합니다. `application.properties`에서 이 파일을 import하여 사용합니다.

   Properties

    ```
    # src/main/resources/application-secret.properties
    spring.ai.openai.api-key=YOUR_OPENAI_API_KEY
    spring.datasource.username=YOUR_DB_USERNAME
    spring.datasource.password=YOUR_DB_PASSWORD
    ```

   또한, `src/main/resources/application.properties` 파일에서 DB URL 및 자막 추출 전략 등 주요 설정을 변경할 수 있습니다.

3. **애플리케이션 실행**

   Bash

    ```
    ./gradlew bootRun
    ```


### API 사용 예시

1. **작업 요청**

    - YouTube 영상 URL을 `url` 파라미터로 전달하여 작업을 요청합니다.

    - 성공 시 `202 Accepted` 응답과 함께 `jobId`를 반환받습니다. `jobId`는 YouTube 영상의 ID와 동일합니다.


    Bash
    
    ```
    curl -X POST "http://localhost:8080/api/jobs/subtitles?url=YOUTUBE_VIDEO_URL"
    ```

2. **실시간 상태 스트림 구독**

    - 위에서 받은 `jobId`를 사용하여 SSE 스트림을 구독합니다.

    - `text/event-stream` 형식으로 작업 상태가 실시간으로 전송됩니다.


    Bash
    
    ```
    curl -N "http://localhost:8080/api/jobs/subtitles/stream/{jobId}"
    ```
    
    - **수신 가능 이벤트**: `pending`, `progress`, `complete`, `error`
        
    - **데이터 형식**: `JobStatusDto` (jobId, status, result)

3. **작업 상태 조회**

    - 특정 작업의 현재 상태를 직접 조회할 수 있습니다.


    Bash
    
    ```
    curl -X GET "http://localhost:8080/api/jobs/subtitles/status/{jobId}"
    ```


## ⚙️ 주요 설정

`src/main/resources/application.properties` 파일에서 서비스의 핵심 동작을 제어할 수 있습니다.

- **자막 추출 방식 변경**

    - `app.subtitle.provider`: `ytDlp`(기본값) 또는 `youtubeApi` 중 선택하여 자막 추출 전략을 변경할 수 있습니다.

- **AI 처리 전략 튜닝**

    - `app.ai.strategy.optimal-chars`: Map-Reduce 전략이 적용되는 글자 수 기준을 조정할 수 있습니다.

    - `app.ai.strategy.max-chunks`: 최대 분할 청크 수를 제한합니다.

- **프롬프트 커스터마이징**

    - `app.ai.prompt.*`: AI에게 전달할 프롬프트를 요구사항에 맞게 직접 수정할 수 있습니다.

- **yt-dlp 프록시 및 쿠키 설정**

    - `proxy.enabled`, `proxy.url`, `app.ytdlp.cookie-path` 등을 통해 `yt-dlp` 실행 시 프록시나 로그인 쿠키를 사용하도록 설정할 수 있습니다.


## 📝 개선 과제 (TODO)

- **오류 처리 고도화**: 일시적인 네트워크 오류(AI API 호출, 자막 다운로드 등)에 대응하기 위해 Spring Retry 등을 활용한 재시도 로직을 추가하여 시스템 안정성을 높일 수 있습니다.

- **보안 강화**: API Rate Limiting(요청량 제한)을 도입하여 비정상적인 트래픽으로부터 시스템과 API 비용을 보호해야 합니다.

- **분산 환경 지원**: 현재 `SseEmitter`가 단일 인스턴스 메모리에서 관리되는 부분을 Redis Pub/Sub 등으로 교체하여, 여러 인스턴스로 확장 가능한 분산 환경을 지원하도록 아키텍처를 개선할 수 있습니다.


## 📜 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.