# Spring Error Lens

Spring Boot 애플리케이션에서 발생하는 에러를 자동으로 수집하여 Discord 또는 Telegram으로 알림을 전송하는 라이브러리입니다.

`log.error(...)` 한 줄만 있어도 자동으로 수집되며, 하나의 HTTP 요청·스케줄러 실행·비동기 작업에서 발생한 여러 에러를 하나의 알림으로 묶어서 전송합니다. 선택적으로 LLM 분석 결과를 함께 전달할 수 있습니다.

---

## 목차

- [요구사항](#요구사항)
- [설치](#설치)
- [빠른 시작](#빠른-시작)
- [동작 방식](#동작-방식)
- [설정 레퍼런스](#설정-레퍼런스)
- [알림 채널 설정](#알림-채널-설정)
- [LLM 분석 설정](#llm-분석-설정)
- [고급 설정](#고급-설정)
- [알림 메시지 예시](#알림-메시지-예시)
- [커스터마이징](#커스터마이징)
- [주의사항](#주의사항)

---

## 요구사항

- Java 17 이상
- Spring Boot 3.x
- Logback (Spring Boot 기본 내장)

---

## 설치

Nexus 저장소를 통해 배포됩니다. 별도 인증 없이 아래와 같이 저장소와 의존성을 추가하세요.

### Gradle (Groovy DSL)

```groovy
repositories {
    maven { url 'https://nexus.chuseok22.com/repository/maven-releases' }
    mavenCentral()
}

dependencies {
    implementation 'com.chuseok22:error-alert-spring-boot-starter:+'  // 항상 최신 버전
}
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven { url = uri("https://nexus.chuseok22.com/repository/maven-releases") }
    mavenCentral()
}

dependencies {
    implementation("com.chuseok22:error-alert-spring-boot-starter:+")  // 항상 최신 버전
}
```

### Maven

Maven은 동적 버전 지정을 권장하지 않습니다.
[GitHub Releases](https://github.com/Chuseok22/spring-error-lens/releases)에서 최신 버전을 확인 후 명시하세요.

```xml
<repositories>
  <repository>
    <id>chuseok22-nexus</id>
    <url>https://nexus.chuseok22.com/repository/maven-releases</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.chuseok22</groupId>
  <artifactId>error-alert-spring-boot-starter</artifactId>
  <version>0.1.2</version>
</dependency>
```

> **Gradle `+` 버전 사용 시 주의:** Gradle은 한 번 해석한 `+` 버전 결과를 캐시합니다.
> 새 버전이 배포된 후 즉시 반영하려면 `./gradlew dependencies --refresh-dependencies`를 실행하세요.

---

## 빠른 시작

### 1단계: 의존성 추가

위 [설치](#설치) 섹션 참고.

### 2단계: `application.yml` 설정

**Discord 알림 예시:**

```yaml
error-alert:
  environment: prod
  discord:
    enabled: true
    webhook-url: https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN
```

**Telegram 알림 예시:**

```yaml
error-alert:
  environment: prod
  telegram:
    enabled: true
    bot-token: YOUR_BOT_TOKEN
    chat-id: YOUR_CHAT_ID
```

### 3단계: 완료

별도의 코드 변경 없이 `log.error(...)` 로그가 자동으로 수집되어 알림이 전송됩니다.

```java
@RestController
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
        try {
            return ResponseEntity.ok(orderService.create(request));
        } catch (Exception e) {
            log.error("주문 생성 실패", e);  // 자동으로 알림 전송
            throw e;
        }
    }
}
```

---

## 동작 방식

### 에러 수집 경로

라이브러리는 세 가지 실행 단위에서 에러를 수집합니다.

| 실행 단위 | 수집 방식 | 집계 타입 |
|-----------|-----------|-----------|
| HTTP 요청 | Servlet Filter + MVC Interceptor | `REQUEST` |
| `@Scheduled` 메서드 | AOP `@Around` 어드바이스 | `SCHEDULER` |
| `@Async` void 메서드 | `AsyncUncaughtExceptionHandler` | `ASYNC` |
| 그 외 (배치, 초기화 등) | Logback Appender 단독 동작 | `STANDALONE` |

### 에러 집계

하나의 실행 단위(예: 하나의 HTTP 요청) 안에서 발생한 여러 에러는 하나의 알림으로 묶입니다.

```
HTTP 요청 하나에서 발생한 에러들:
  ├── log.error("상품 재고 부족")         → Related Error
  ├── log.error("결제 실패", exception)   → Primary Error (예외가 있으므로 우선 선택)
  └── throw RuntimeException(...)         → MVC 인터셉터가 캐치

→ 알림 1건: Primary Error = "결제 실패", Related = 1건
```

**Primary Error 선택 우선순위:**
1. 예외(Throwable) + ERROR 레벨 로그
2. 예외(Throwable) + WARN 레벨 로그
3. ERROR 레벨 로그
4. WARN 레벨 로그

### 처리 흐름

```
에러 발생
    │
    ├─ Logback Appender 캡처 (log.error)
    ├─ MVC Interceptor 캡처 (미처리 예외)
    ├─ Scheduler AOP 캡처 (@Scheduled 예외)
    └─ Async Handler 캡처 (@Async void 예외)
         │
         ▼
    ErrorAlertQueue (비동기 큐, 기본 1000건)
         │
         ▼
    ErrorAlertWorker (백그라운드 스레드)
         │
         ├─ LLM 분석 (설정 시)
         └─ Discord / Telegram 전송
```

에러 수집은 메인 스레드에서 동기적으로 이루어지지만, LLM 분석과 알림 전송은 별도 백그라운드 스레드에서 처리되므로 애플리케이션 성능에 영향을 주지 않습니다.

---

## 설정 레퍼런스

### 전체 설정 예시

```yaml
error-alert:
  # 라이브러리 활성화 여부 (기본값: true)
  enabled: true

  # 환경 이름 - 알림 메시지에 표시됨 (기본값: prod)
  environment: prod

  application:
    # 서비스 이름 - 미설정 시 spring.application.name 사용
    name: ${spring.application.name}
    # 버전 - 미설정 시 build-info > Manifest > unknown 순으로 자동 감지
    version: 1.0.0

  logging:
    # WARN 레벨 로그도 알림 대상에 포함할지 여부 (기본값: false)
    warn-enabled: false

  queue:
    # 에러 이벤트 큐 최대 크기 (기본값: 1000)
    capacity: 1000

  aggregation:
    # @Scheduled 집계 활성화 여부 (기본값: true)
    scheduler-enabled: true
    # @Async 집계 활성화 여부 (기본값: true)
    async-enabled: true

  llm:
    # LLM 분석 활성화 여부 (기본값: true, endpoint 미설정 시 자동 비활성화)
    enabled: true
    endpoint: https://your-llm-api/v1/chat/completions
    api-key: ${LLM_API_KEY}
    model: gpt-4o-mini
    # API 요청 타임아웃 (기본값: 10s)
    timeout: 10s
    cache:
      # 동일 에러 패턴에 대한 LLM 결과 캐시 여부 (기본값: true)
      enabled: true
      # 캐시 TTL (기본값: 30m)
      ttl: 30m

  discord:
    # Discord 알림 활성화 여부 (기본값: false)
    enabled: false
    webhook-url: https://discord.com/api/webhooks/...

  telegram:
    # Telegram 알림 활성화 여부 (기본값: false)
    enabled: false
    bot-token: ${TELEGRAM_BOT_TOKEN}
    chat-id: ${TELEGRAM_CHAT_ID}
```

### 설정 항목 상세

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `error-alert.enabled` | `true` | 라이브러리 전체 활성화 여부 |
| `error-alert.environment` | `prod` | 환경 이름 (알림 메시지에 표시) |
| `error-alert.application.name` | `spring.application.name` | 서비스 이름 |
| `error-alert.application.version` | 자동 감지 | 버전 (미설정 시 build-info → Manifest → unknown) |
| `error-alert.logging.warn-enabled` | `false` | WARN 로그 알림 포함 여부 |
| `error-alert.queue.capacity` | `1000` | 내부 큐 최대 이벤트 수 (초과 시 새 이벤트 DROP) |
| `error-alert.aggregation.scheduler-enabled` | `true` | `@Scheduled` 집계 활성화 |
| `error-alert.aggregation.async-enabled` | `true` | `@Async` 집계 활성화 |
| `error-alert.llm.enabled` | `true` | LLM 분석 활성화 |
| `error-alert.llm.endpoint` | 없음 | LLM API 엔드포인트 (미설정 시 LLM 비활성화) |
| `error-alert.llm.api-key` | 없음 | LLM API 인증 키 |
| `error-alert.llm.model` | 없음 | 사용할 모델명 |
| `error-alert.llm.timeout` | `10s` | LLM API 요청 타임아웃 |
| `error-alert.llm.cache.enabled` | `true` | 동일 에러 패턴 결과 캐싱 여부 |
| `error-alert.llm.cache.ttl` | `30m` | LLM 캐시 유지 시간 |
| `error-alert.discord.enabled` | `false` | Discord 알림 활성화 |
| `error-alert.discord.webhook-url` | 없음 | Discord Webhook URL |
| `error-alert.telegram.enabled` | `false` | Telegram 알림 활성화 |
| `error-alert.telegram.bot-token` | 없음 | Telegram Bot Token |
| `error-alert.telegram.chat-id` | 없음 | Telegram Chat ID |

---

## 알림 채널 설정

### Discord Webhook 설정

1. Discord 서버 채널 설정 → 연동 → 웹후크 → 새 웹후크 생성
2. 웹후크 URL 복사
3. `application.yml`에 설정:

```yaml
error-alert:
  discord:
    enabled: true
    webhook-url: https://discord.com/api/webhooks/1234567890/abcdefghijk...
```

### Telegram Bot 설정

1. Telegram에서 [@BotFather](https://t.me/BotFather)에게 `/newbot` 명령 전송
2. Bot Token 발급
3. 알림을 받을 채팅방(또는 그룹)에 Bot 초대
4. Chat ID 확인:
   ```
   https://api.telegram.org/bot{BOT_TOKEN}/getUpdates
   ```
   응답의 `result[].message.chat.id` 값 사용
5. `application.yml`에 설정:

```yaml
error-alert:
  telegram:
    enabled: true
    bot-token: 1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ
    chat-id: -1001234567890
```

> Discord와 Telegram을 동시에 활성화하면 두 채널 모두 알림이 전송됩니다.

---

## LLM 분석 설정

에러 발생 시 LLM이 원인을 분석하여 알림에 함께 표시합니다.
OpenAI API 호환 형식(`/v1/chat/completions`)을 사용하는 엔드포인트라면 어떤 LLM 서비스든 연동 가능합니다.

```yaml
error-alert:
  llm:
    enabled: true
    endpoint: https://api.openai.com/v1/chat/completions
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini
    timeout: 15s
    cache:
      ttl: 1h  # 동일 에러 패턴이 반복될 때 LLM 재호출 방지
```

**동일 에러 패턴 캐싱:**
UUID, IP, 이메일, 날짜시간 등 가변 값을 정규화한 후 에러 패턴을 키로 사용하기 때문에, 같은 유형의 에러가 반복 발생해도 LLM 호출은 한 번만 이루어집니다. 기본 TTL은 30분입니다.

**LLM 비활성화:**
`endpoint`를 설정하지 않으면 자동으로 LLM 분석이 비활성화됩니다.

```yaml
error-alert:
  llm:
    enabled: false  # 또는 endpoint를 설정하지 않으면 자동 비활성화
```

---

## 고급 설정

### 버전 자동 감지

`error-alert.application.version`을 설정하지 않으면 아래 순서로 버전을 자동으로 감지합니다.

1. Spring Boot `build-info` 플러그인 정보
2. JAR Manifest의 `Implementation-Version`
3. `error-alert.application.version` 직접 설정값
4. `"unknown"`

**`build-info` 활성화 (권장):**

```groovy
// build.gradle
springBoot {
    buildInfo()
}
```

### 환경별 설정

`application-{profile}.yml`을 활용한 환경별 설정 예시:

```yaml
# application-local.yml
error-alert:
  enabled: false  # 로컬에서는 알림 비활성화
```

```yaml
# application-prod.yml
error-alert:
  environment: prod
  discord:
    enabled: true
    webhook-url: ${DISCORD_WEBHOOK_URL}
  llm:
    enabled: true
    endpoint: ${LLM_ENDPOINT}
    api-key: ${LLM_API_KEY}
    model: gpt-4o-mini
```

### WARN 레벨 포함

기본적으로 `log.error(...)` 만 수집됩니다. WARN 레벨도 포함하려면:

```yaml
error-alert:
  logging:
    warn-enabled: true
```

### 대용량 트래픽 환경 큐 조정

기본 큐 용량(1000건)이 부족한 경우:

```yaml
error-alert:
  queue:
    capacity: 5000
```

큐가 가득 차면 새로운 에러 이벤트는 DROP됩니다. 로그에 경고 메시지가 출력됩니다.

---

## 알림 메시지 예시

### Discord 알림

```
🚨 my-service — 에러 알림
버전: 1.2.3 | 환경: prod
집계 타입: REQUEST

요청: POST /api/orders

Primary Error: 재고 부족으로 주문 처리 실패
예외: io.myapp.exception.OutOfStockException

LLM 분석:
재고 수량이 0인 상태에서 주문이 시도되었습니다.
OutOfStockException은 재고 검증 로직에서 발생하며,
동시 요청 처리 시 Race Condition 가능성이 있습니다.
재고 감소 로직에 낙관적 락 또는 분산 락 도입을 검토하세요.

스택 트레이스:
io.myapp.exception.OutOfStockException: 재고 부족
    at io.myapp.service.InventoryService.decrease(InventoryService.java:42)
    at io.myapp.service.OrderService.create(OrderService.java:87)
    ...
```

### Telegram 알림

Discord와 동일한 내용이 Telegram 메시지로 전송됩니다. 4000자를 초과하는 경우 자동으로 분할 전송됩니다.

---

## 커스터마이징

### 커스텀 알림 채널 추가

`NotificationChannel` 인터페이스를 구현한 뒤, `NotificationDispatcher` Bean을 직접 등록하여 교체하세요.
기본 `NotificationDispatcher`는 `@ConditionalOnMissingBean`이 적용되어 있으므로, 직접 등록한 Bean이 우선합니다.

```java
@Configuration
public class AlertConfig {

    @Bean
    public NotificationDispatcher notificationDispatcher(ErrorAlertProperties props) {
        List<NotificationChannel> channels = new ArrayList<>();

        // 기존 Discord/Telegram 채널 유지
        if (props.getDiscord().isEnabled()) {
            channels.add(new DiscordNotificationChannel(props.getDiscord().getWebhookUrl()));
        }

        // 커스텀 Slack 채널 추가
        channels.add(new SlackNotificationChannel("https://hooks.slack.com/..."));

        return new NotificationDispatcher(channels);
    }
}
```

```java
public class SlackNotificationChannel implements NotificationChannel {

    private final String webhookUrl;

    public SlackNotificationChannel(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override public String getName() { return "slack"; }
    @Override public boolean isEnabled() { return webhookUrl != null; }

    @Override
    public void send(MergedErrorEvent event) {
        // Slack 전송 로직
    }
}
```

### 커스텀 LLM 클라이언트 연동

사내 LLM 또는 특수한 API 형식을 사용하는 경우 `LlmClient` 인터페이스를 구현하고 Bean으로 등록하세요.

```java
@Bean
public LlmClient customLlmClient() {
    return event -> {
        String prompt = buildMyPrompt(event);
        String result = myLlmService.analyze(prompt);
        return LlmAnalysisResult.success(result);
    };
}
```

`@ConditionalOnMissingBean`이 적용되어 있으므로, 커스텀 Bean이 등록되면 기본 `InternalLlmClient`는 등록되지 않습니다.

### 커스텀 버전 리졸버

```java
@Bean
public ApplicationVersionResolver customVersionResolver() {
    return () -> System.getenv("APP_VERSION");
}
```

---

## 주의사항

### 민감 정보 필터링

Request body가 알림 메시지에 포함될 수 있습니다. 비밀번호, 카드번호 등 민감 정보가 포함된 엔드포인트는 별도 필터링 처리를 권장합니다.

### Multipart 요청

`multipart/form-data` 요청은 body 캐싱 대상에서 제외됩니다.

### @Async 집계

`@Async void` 메서드의 예외는 집계 없이 단건으로 즉시 알림이 전송됩니다. `@Async` 메서드가 `Future` 또는 `CompletableFuture`를 반환하는 경우는 자동 수집 대상이 아닙니다.

### 알림 중복 방지

알림 전송 자체에서 발생하는 로그(예: Discord 전송 실패 WARN)는 `AlertGuard`에 의해 자동으로 무시됩니다. 알림이 알림을 유발하는 재귀 루프는 발생하지 않습니다.

### 큐 초과 시 동작

에러 발생 속도가 처리 속도를 초과하면 큐가 가득 차고, 이후 에러 이벤트는 DROP됩니다. 큐 초과 시 `WARN` 로그가 출력됩니다.

---

## 라이선스

MIT License
