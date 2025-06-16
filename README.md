# YouTube AI Summary

![Java](https://img.shields.io/badge/Java-17+-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen) ![Gradle](https://img.shields.io/badge/Gradle-8.14-blue) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

YouTube 영상의 자막을 추출하고 AI를 통해 가독성 높은 문어체 아티클 형식으로 재구성하는 비동기 처리 백엔드 서비스입니다. 사용자는 작업 요청 후 즉시 응답을 받고, Server-Sent Events(SSE)를 통해 처리 과정을 실시간으로 확인할 수 있습니다.

## ✨ 주요 특징

- **비동기/논블로킹 아키텍처**: `CompletableFuture`와 전용 스레드 풀(`@Async`)을 사용하여 자막 추출, AI 호출 등 시간이 오래 걸리는 작업을 효율적으로 처리하고 시스템 리소스를 최적으로 사용합니다.
- **실시간 진행 상황 알림**: Server-Sent Events(SSE)를 통해 클라이언트에게 작업의 각 단계를 실시간으로 알려주어 서비스의 응답성과 사용자 경험을 극대화합니다.
- **전략 패턴 기반의 유연한 설계**: 자막 추출 방식을 `yt-dlp` 또는 `youtube-transcript-api` 중에서 선택할 수 있도록 전략 패턴을 적용했습니다. `application.properties` 파일에서 설정값 하나만 변경하여 자막 추출 구현체를 쉽게 교체할 수 있습니다.
- **지능적인 AI 처리 전략**: Spring AI를 활용하며, 입력된 텍스트의 길이에 따라 다른 처리 전략(단일 요청 또는 Map-Reduce)을 동적으로 선택합니다. 이를 통해 AI 모델의 토큰 제한을 효과적으로 관리하고 비용 효율성을 높입니다.
- **명확한 역할 분리(SoC)**: `OrchestrationService`는 작업 흐름을, `JobManager`는 상태 관리를 전담하는 등 각 컴포넌트의 역할과 책임을 명확히 분리하여 코드의 응집도를 높이고 유지보수성을 향상시켰습니다.

## 🚀 시작하기

### 요구사항

- Java (JDK) 17 이상
- Gradle 8.0 이상
- `yt-dlp`: 시스템에 설치되어 있고 PATH에 등록되어 있어야 합니다.
- Spring AI 연동을 위한 API Key (예: OpenAI API Key)

### 실행 방법

1.  **프로젝트 클론**
    ```bash
    git clone [https://github.com/your-username/youtube-ai-summary.git](https://github.com/your-username/youtube-ai-summary.git)
    cd youtube-ai-summary
    ```

2.  **설정 파일 수정**
    `src/main/resources/application.properties` 파일을 열어 자신의 환경에 맞게 설정을 수정합니다. 특히 Spring AI 관련 API 키 설정이 필요합니다.
    ```
    # application.properties
    # 예시: OpenAI API 키 설정
    spring.ai.openai.api-key=YOUR_OPENAI_API_KEY
    
    # yt-dlp 경로 설정 (PATH에 등록된 경우 'yt-dlp' 유지)
    app.ytdlp.path=yt-dlp
    ```

3.  **애플리케이션 실행**
    ```bash
    ./gradlew bootRun
    ```

### API 사용 예시
1. **작업 요청**
	```bash curl -X POST "http://localhost:8080/api/jobs/subtitles?url=[https://www.youtube.com/watch?v=유튜브영상ID](https://www.youtube.com/watch?v=유튜브영상ID)" ``` 
	 - 성공 시 `jobId`가 포함된 `202 Accepted` 응답을 받습니다. 
2. **실시간 상태 스트림 구독**
	 - 위에서 받은 `jobId`를 사용합니다. 
	 ```bash curl -N "http://localhost:8080/api/jobs/subtitles/stream/{jobId}" ```

## 📐 아키텍처 및 동작 방식

이 시스템은 요청을 즉시 수락하고 백그라운드에서 비동기적으로 작업을 처리하는 이벤트 기반 아키텍처를 따릅니다. 작업의 전체 생명주기와 시스템 컴포넌트 간의 상호작용은 아래 다이어그램을 통해 상세히 이해할 수 있습니다.

### 작업 상태 흐름 (State Diagram)

하나의 작업(Job)이 생성되어 완료되거나 실패하기까지의 모든 상태 변화 경로를 보여줍니다.

![](https://i.imgur.com/1xDmp8r.png)


### 시스템 상호작용 (Sequence Diagram)

사용자의 요청부터 최종 결과를 받기까지의 과정을 시간 순서대로 보여줍니다.

![](https://i.imgur.com/H4dzHZp.png)


## 🗂️ 프로젝트 구조

-   **`src/main`**: 메인 애플리케이션 소스 코드
    -   **`java/org/example/youtubeaisummary`**: 자바 소스 코드 루트
        -   `config`: 애플리케이션 설정 (비동기, Bean 등)
        -   `controller`: API 엔드포인트
        -   `converter`: 타입 변환 로직
        -   `dto`: 데이터 전송 객체
        -   `exception`: 사용자 정의 예외
        -   `repository`: 데이터 저장소 (In-Memory)
        -   `service`: 핵심 비즈니스 로직
            -   `ai`: AI 관련 서비스
            -   `subtitle`: 자막 추출 관련 서비스
        -   `vo`: 값 객체 (Value Object)
    -   **`resources`**: 설정 파일
        -   `application.properties`: 핵심 설정 파일
-   **`src/test`**: 단위 테스트 코드
-   **`build.gradle`**: Gradle 빌드 스크립트

## ⚙️ 주요 설정

`application.properties` 파일에서 서비스의 주요 동작을 제어할 수 있습니다.

-   **자막 추출 방식 변경**
    -   `app.subtitle.provider`: `ytDlp`(기본값) 또는 `youtubeApi` 중 선택하여 자막 추출 전략을 변경할 수 있습니다.
-   **AI 처리 전략 튜닝**
    -   `app.ai.strategy.optimal-chars`: Map-Reduce 전략이 적용되는 글자 수 기준을 조정할 수 있습니다.
    -   `app.ai.strategy.max-chunks`: 최대 분할 청크 수를 제한합니다.
-   **프롬프트 수정**
    -   `app.ai.prompt.*`: AI에게 전달할 시스템 프롬프트를 요구사항에 맞게 직접 수정할 수 있습니다.

## 📝 개선 과제 (TODO)

-   **상태 저장소 개선**: 현재 In-Memory 방식의 `JobRepository`를 Redis 또는 RDB로 교체하여 데이터 영속성을 확보하고 분산 환경을 지원.
-   **오류 처리 고도화**: 일시적인 네트워크 오류 등에 대응하기 위해 Spring Retry 등을 활용한 재시도 로직 추가.
-   **보안 강화**: API Rate Limiting을 도입하여 비정상적인 트래픽으로부터 시스템과 비용을 보호.
-   **컨테이너화**: Dockerfile을 작성하여 `yt-dlp` 등 외부 의존성을 포함한 배포 환경을 표준화.

## 📜 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.
