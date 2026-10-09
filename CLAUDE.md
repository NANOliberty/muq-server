# 뮤큐(MuQ) 서버 — Claude Code 작업 규칙

뮤큐는 서로 다른 음악 앱(Spotify/YouTube/Apple Music)을 쓰는 친구들이 공유 큐를 함께 채우고, 친구가 듣는 곡을 내 플레이어로 따라 듣는 서비스다. 기획서는 `docs/plan.md`.

## 이 프로젝트의 최우선 목표

이 저장소는 **NANO가 Spring Boot 백엔드를 깊이 이해하는 것**이 1순위고, 기능 완성은 2순위다.
NANO는 iOS 개발자이며 Spring Boot 경험은 있지만 "왜 이렇게 짜는지"까지 체화하고 싶어 한다.
빠르게 많이 짜는 것보다, **한 번에 하나를 설명 가능한 크기로** 짜는 것을 우선한다.

**단, "이해하기 쉽게"를 이유로 설계를 낮추지 않는다.** 코드는 실무에서 그대로 쓸 수 있는 수준이어야 한다.
- 성능·동시성·확장성이 필요한 곳에는 그에 맞는 구조(Lua 스크립트, 분산락, 비동기 호출, 캐시)를 그대로 쓴다. 쉬운 버전으로 대체하지 않는다.
- 이해를 돕는 수단은 **설명과 주석과 학습 노트**이지, 단순화된 설계가 아니다. 어려운 코드가 나오면 그 코드를 더 자세히 설명한다.
- 어떤 패턴이 "포트폴리오용으로 보기 좋아서"가 아니라 "실제로 필요해서" 들어가는지 매번 근거를 댄다. 필요 없으면 넣지 않는다.
- 한 턴에 한 레이어·30줄 제한은 작업을 쪼개는 단위일 뿐, 전체 구조를 작게 만들라는 뜻이 아니다.

## 작업 방식 (반드시 지킬 것)

1. **설계 먼저, 코드는 승인 후.** 기능 요청을 받으면 바로 코드를 쓰지 않는다. 먼저 (a) 어떤 클래스가 생기고 (b) 요청이 어떤 순서로 흐르고 (c) 왜 그 구조인지 5~10줄로 설명한 뒤 "이대로 갈까요?"를 묻는다.
2. **한 턴에 한 레이어.** Controller → Service → Repository/Redis → 테스트 순서로, 각 레이어를 별도 턴에 짠다. 한 번에 파일 5개 이상 만들지 않는다.
3. **따라 칠 수 있는 코드.** 클래스 상단에 "이 클래스가 하는 일" 주석 2~3줄, 비자명한 메서드에는 `// 왜:` 주석을 단다. 한 메서드는 30줄을 넘기지 않는다. 람다 체이닝은 한 줄에 하나씩 끊는다.
4. **기능 하나가 끝나면 학습 노트.** `/learning-note` 스킬로 `docs/learning/NN-<topic>.md`를 쓴다. (요청 흐름 · 핵심 코드 발췌 · 따라치기 순서 · 스스로 답해볼 질문 3개)
5. **설계 결정은 ADR.** 라이브러리 선택, 자료구조 선택, 동시성 전략처럼 "다른 선택지가 있었던 것"은 `docs/adr/NNNN-<title>.md`에 남긴다. 템플릿은 `docs/adr/0000-template.md`.
6. **커밋은 NANO가 직접 한다.** Claude는 `git commit`을 실행하지 않는다. 대신 작업이 끝나면 커밋 메시지 초안(아래 컨벤션)을 제안하고, NANO가 검토 후 직접 커밋한다. `git push`도 하지 않는다.

## 기술 스택

- Java 21, Spring Boot 3.5, Gradle (Kotlin DSL)
- PostgreSQL 16 + Spring Data JPA + Flyway
- Redis 7 + Redisson (Sorted Set, Pub/Sub, 분산락, TTL)
- Spring WebSocket + STOMP (브로커는 Redis Pub/Sub 릴레이)
- Spring Security + OAuth2 Client + JWT
- WebClient + Resilience4j (외부 API)
- springdoc-openapi, Bean Validation
- JUnit 5, Testcontainers(Postgres·Redis), WireMock(Spotify·YouTube 목)
- Docker / docker-compose, GitHub Actions

## 패키지 구조 (도메인 기준)

```
com.muq
├── global/          # 전역 설정·예외·공통 응답 (config, error, security, web)
├── auth/            # 로그인, JWT, ProviderAccount
├── user/            # User, Friendship, visibility
├── room/            # Room, RoomMember, 큐, 투표, 리액션  ← 핵심
├── track/           # Track, TrackMapping, 검색, 매칭
├── provider/        # Spotify / YouTube / Apple 클라이언트 (전략 패턴)
├── sync/            # 재생 상태 브로드캐스트, 따라 듣기, 리슨 링크
└── realtime/        # WebSocket/STOMP 설정, 메시지 핸들러, Redis Pub/Sub 릴레이
```

각 도메인 패키지 안은 `controller / service / domain / repository / dto` 로 나눈다. 도메인 간 참조는 service 레이어에서만 한다.

## 코드 컨벤션

- 엔티티는 setter 금지. 상태 변경은 의도가 드러나는 메서드로 (`room.lock()`, `queue.vote(userId)`).
- DTO는 record. 요청은 `XxxRequest`, 응답은 `XxxResponse`.
- 예외는 `global/error`의 `BusinessException(ErrorCode)` 하나로 통일. ErrorCode는 enum.
- Redis 키는 `global/redis/RedisKey` 한 곳에서만 만든다. 문자열을 코드 곳곳에 흩뿌리지 않는다.
- 외부 API 응답 DTO는 `provider` 패키지 밖으로 나가지 않는다. 밖으로 나갈 땐 `Track`으로 변환한다.
- 테스트 이름은 한글 가능: `@DisplayName("같은 곡을 두 번 추가하면 투표로 처리된다")`.

## 커밋 메시지 컨벤션

```
<type>(<scope>): <한 줄 요약>

<무엇을, 왜 (선택)>
```
type: feat / fix / refactor / test / docs / chore / infra
scope: room / track / auth / realtime / sync / provider / infra 등 패키지명

## 하지 말 것

- 기획서에 없는 기능을 "있으면 좋을 것 같아서" 추가하지 않는다. 제안은 하되 구현은 승인 후.
- `@Transactional`을 클래스 전체에 붙이지 않는다. 필요한 메서드에만.
- Lombok은 `@Getter`, `@RequiredArgsConstructor`, `@Builder`까지만. `@Data`, `@Setter` 금지.
- 테스트 없는 Service 메서드를 "완료"라고 하지 않는다.
- 외부 API 키, 토큰을 코드나 커밋에 넣지 않는다. 전부 환경변수.

## 자주 쓰는 명령

```bash
docker compose up -d            # Postgres, Redis, WireMock
./gradlew bootRun               # 로컬 실행 (profile: local)
./gradlew test                  # 전체 테스트 (Testcontainers 사용)
./gradlew test --tests '*Room*' # 특정 도메인만
```
