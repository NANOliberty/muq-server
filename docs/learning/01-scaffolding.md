# 01. 프로젝트 스캐폴딩 (M0)

> 한 줄 요약: 이 단계는 "빈 서버가 PostgreSQL·Redis에 실제로 붙어서 뜬다"를 Testcontainers 테스트로 증명하고, 에러 응답 형식을 첫 기능 전에 고정한다.

## 1. 요청이 흐르는 길

**(a) `GET /actuator/health`** — 우리가 짠 컨트롤러는 없다. 의존성과 설정만으로 동작한다.

1. `spring-boot-starter-actuator` — `/actuator/health` 엔드포인트를 등록
2. `application.yml`의 `management.endpoints.web.exposure.include` — HTTP로 노출할 엔드포인트를 고름
3. `DataSourceHealthIndicator` — JPA 스타터가 만든 `DataSource`로 DB에 쿼리
4. `RedisHealthIndicator` — Redisson이 제공한 `RedisConnectionFactory`로 Redis에 PING
5. 전부 UP이면 200, 하나라도 DOWN이면 503

**(b) 컨트롤러가 예외를 던졌을 때**

1. 컨트롤러 또는 서비스 — `throw new BusinessException(ErrorCode.XXX)`
2. `GlobalExceptionHandler.handleBusiness()` — `@RestControllerAdvice`라 모든 컨트롤러의 예외가 여기로 온다
3. `GlobalExceptionHandler.toProblemDetail()` — `ErrorCode`의 상태·문구로 `ProblemDetail`을 만들고 `code`를 붙임
4. 응답: `application/problem+json`, 본문은 `{type, title, status, detail, instance, code}`

## 2. 핵심 코드 (발췌)

`IntegrationTestSupport` — 모든 통합 테스트의 부모.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 왜: 실제 톰캣을 임의 포트로 띄워 HTTP 로 검증
@ActiveProfiles("test")                 // 왜: 기본값 local 로 뜨면 개인 파일 application-local.yml 이 섞인다
public abstract class IntegrationTestSupport {

    @ServiceConnection                  // 왜: 컨테이너의 임의 포트를 datasource 접속 정보로 자동 주입
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")  // 왜: GenericContainer 는 타입으로 서비스 종류를 알 수 없어 이름으로 알려 준다
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {                            // 왜: @Container 는 클래스마다 재시작한다. 여기서 한 번만 띄워 전체가 공유
        POSTGRES.start();
        REDIS.start();
    }
}
```

`GlobalExceptionHandler` — 예상하지 못한 예외 처리.

```java
@ExceptionHandler(Exception.class)
public ProblemDetail handleUnexpected(Exception ex) {
    logger.error("처리되지 않은 예외", ex);               // 왜: 원인은 서버 로그에만
    return toProblemDetail(ErrorCode.INTERNAL_ERROR);   // 왜: ex.getMessage() 에는 SQL·내부 경로가 섞일 수 있다
}
```

## 3. 따라치기 순서

1. `settings.gradle.kts`, `build.gradle.kts` 작성 후 `gradle wrapper --gradle-version 8.14.3`
   → `./gradlew help`가 성공하면 플러그인·의존성 해석이 된 것
2. `MuqApplication.java` 만들기 → `./gradlew compileJava` 통과
3. `application.yml` 만들기 → `docker compose up -d` 후 `./gradlew bootRun`,
   `curl localhost:8080/actuator/health`가 `{"status":"UP", ...}`
   (실험: `flyway-database-postgresql` 한 줄을 지우고 띄워 본다. 어떤 에러가 나는가?)
4. `application-test.yml`, `IntegrationTestSupport.java` 만들기 → 컴파일만 되면 OK
5. `HealthCheckTest.java` 만들기 → `./gradlew test --tests '*HealthCheck*'` 통과
   (실험: `docker compose down` 한 상태에서도 통과하는지 확인한다. 통과해야 한다.)
6. `ErrorCode.java` → `BusinessException.java` → `GlobalExceptionHandler.java` 순서로 만들기
7. `GlobalExceptionHandlerTest.java` 만들기 → `./gradlew test --tests '*GlobalExceptionHandler*'` 통과

## 4. 왜 이렇게 했나 (다른 선택지)

| 결정 | 선택 | 안 고른 것 | 이유 |
| --- | --- | --- | --- |
| 의존성 추가 시점 | 마일스톤별로 | 스택 전체를 처음부터 | security는 넣는 순간 health까지 잠근다. 안 쓰는 의존성은 원인 모를 자동 설정을 만든다 |
| Redis 클라이언트 | Redisson 하나 | Lettuce, 또는 둘 다 | [ADR 0003](../adr/0003-redisson-as-single-redis-client.md) |
| 스키마 관리 | Flyway + `ddl-auto: validate` | `ddl-auto: update` | 운영 DB를 Hibernate가 임의로 바꾸면 추적·롤백이 안 된다. 변경은 SQL 파일로 남긴다 |
| `open-in-view` | `false` | 기본값 `true` | `true`면 응답을 다 쓸 때까지 커넥션을 쥔다. 외부 API 호출이 낀 요청에서 풀이 마른다 |
| 설정 값 | `${ENV:로컬 기본값}` + `.env` import | 프로파일 파일마다 값 복사 | 이름은 `.env.example` 하나가 기준. 운영은 환경변수만 바꾸면 된다 |
| 테스트 DB | Testcontainers 실물 | H2, 임베디드 Redis | Flyway의 PostgreSQL 문법, Lua 스크립트, 분산락은 실물에서만 같은 동작을 한다 |
| 에러 응답 | `ProblemDetail` (RFC 9457) | 자체 `ErrorResponse` DTO | 스프링 기본 예외(404·405)와 형식이 저절로 같아진다 |
| 예외 클래스 | `BusinessException(ErrorCode)` 하나 | 상황별 예외 클래스 | 핸들러가 하나로 끝나고 에러 목록이 enum 한 파일에 모인다 |
| compose Postgres 포트 | 호스트 5433 | 5432 | 맥에 따로 설치한 PostgreSQL이 5432를 먼저 잡으면 앱이 엉뚱한 DB에 붙는다 |

## 5. 스스로 답해볼 질문

1. `GlobalExceptionHandler`는 왜 `ResponseEntityExceptionHandler`를 상속하나? 상속하지 않고 `@ExceptionHandler(Exception.class)`만 두면 없는 경로를 호출했을 때 상태 코드가 무엇이 되나?
2. Redis가 죽은 채로 서버를 띄우면 `/actuator/health`는 무엇을 응답하나? `db`만 죽었을 때와 응답이 어떻게 다른가?
3. 통합 테스트 클래스가 30개로 늘어도 컨테이너와 스프링 컨텍스트가 한 번만 뜨려면 어떤 조건이 지켜져야 하나? 어떤 애너테이션을 테스트 클래스에 붙이면 그 조건이 깨지나?

## 6. 확인 명령

```bash
./gradlew test                                   # 전체 (5개)
./gradlew test --tests '*HealthCheck*'           # 컨테이너를 띄우는 통합 테스트
./gradlew test --tests '*GlobalExceptionHandler*' # 웹 계층만 올리는 슬라이스 테스트
docker compose up -d && ./gradlew bootRun        # 로컬 실행 후 curl localhost:8080/actuator/health
```
