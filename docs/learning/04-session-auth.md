# 04. 세션 인증 (Spring Security)

> 한 줄 요약: 이 기능은 "이 요청을 누가, 어느 방에서 보냈나"를, Bearer 토큰 → Redis 세션 → 참여자로 인증하고 경로의 방 코드와 비교하는 것으로 해결한다.

## 1. 요청이 흐르는 길

`GET /api/v1/rooms/K7M2QX/queue` + `Authorization: Bearer <token>` (큐 API는 다음 기능. 지금은 테스트 전용 `test-whoami`로 확인)

1. `AuthenticationFilter` — 요청이 `PublicEndpoints.MATCHER`에 맞으면 아무것도 안 하고 통과. 아니면 다음으로
2. `BearerTokenAuthenticationConverter.convert()` — 헤더 없음·`Basic` → `null`(익명으로 계속), 형식이 틀린 Bearer → `BadCredentialsException`, 정상 → 미인증 `GuestTokenAuthentication(token)`
3. `ProviderManager` → `GuestTokenAuthenticationProvider.authenticate()` — `GuestSessionRedisRepository.findByToken()`. 있으면 인증된 `GuestTokenAuthentication(ParticipantPrincipal)`, 없으면 `BadCredentialsException`
4. 성공 → `SecurityContext`에 저장, 토큰은 `eraseCredentials()`로 지워짐 / 실패 → `ProblemDetailSecurityErrorHandler.commence()` → 401 + `WWW-Authenticate`
5. `AnonymousAuthenticationFilter` — 아직 인증이 없으면(토큰 없음) 익명 사용자를 넣음
6. `AuthorizationFilter` — 규칙을 위에서부터: 공개 경로 → 허용 / `/api/v1/rooms/{code}/**` → `RoomMemberAuthorizationManager.check()` / 나머지 → 인증 필요
7. 거부되면 `ExceptionTranslationFilter` — 익명이면 `commence()`(401), 인증된 사람이면 `handle()`(403 `ROOM_ACCESS_DENIED`)
8. 통과 → 컨트롤러가 `@AuthenticationPrincipal ParticipantPrincipal me`로 참여자를 받음

## 2. 핵심 코드 (발췌)

`SecurityConfig` — 인증 필터를 만드는 부분.

```java
private AuthenticationFilter guestTokenAuthenticationFilter() {           // 왜 @Bean 이 아닌가: 빈이면 서블릿 필터로도 등록돼 두 번 돈다
    AuthenticationFilter filter = new AuthenticationFilter(
            new ProviderManager(authenticationProviders),                   // 왜 목록: M2 의 JWT Provider 가 그대로 들어온다
            new BearerTokenAuthenticationConverter());
    filter.setRequestMatcher(new NegatedRequestMatcher(PublicEndpoints.MATCHER));  // 왜: 공개 경로는 만료된 토큰을 들고 와도 막지 않는다
    filter.setSuccessHandler((request, response, authentication) -> {       // 왜 비우나: 기본값은 로그인 폼용이라 "/" 로 리다이렉트한다
    });
    filter.setFailureHandler(new AuthenticationEntryPointFailureHandler(securityErrorHandler));  // 왜: 틀린 토큰도 같은 401 응답
    return filter;
}
```

`RoomMemberAuthorizationManager` — 방 단위 권한.

```java
public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
    // 왜 타입을 보나: 익명 사용자도 isAuthenticated() 는 true 다. 세션 토큰으로 인증된 사람만 참여자다
    if (!(authentication.get().getPrincipal() instanceof ParticipantPrincipal participant)) {
        return new AuthorizationDecision(false);                           // 익명 거부 → ExceptionTranslationFilter 가 401 로 바꾼다
    }
    String requestedRoomCode = context.getVariables().get(ROOM_CODE_VARIABLE);   // 경로 /rooms/{code}/** 의 {code}
    boolean sameRoom = requestedRoomCode != null && participant.roomCode().equalsIgnoreCase(requestedRoomCode);
    return new AuthorizationDecision(sameRoom);                            // 다른 방 → 403
}
```

## 3. 따라치기 순서

1. `build.gradle.kts`에 `spring-boot-starter-security` 추가만 하고 `./gradlew test`
   → `@WebMvcTest`가 401·403으로 깨지는 것을 직접 본다 (Boot 기본 보안: 전부 잠금, CSRF 켜짐)
2. `SecurityConfig`(공개 경로·STATELESS·CSRF 끄기) → `RoomControllerTest`에 `@Import(SecurityConfig.class)`, `GlobalExceptionHandlerTest`에 `@AutoConfigureMockMvc(addFilters = false)` → 다시 통과
3. `ProblemDetails` 추출 → `ErrorCode.UNAUTHORIZED`·`ROOM_ACCESS_DENIED` → `ProblemDetailSecurityErrorHandler` → 테스트 통과
4. `ParticipantPrincipal` → `GuestTokenAuthentication` → `BearerTokenAuthenticationConverter` → 단위 테스트 통과
5. `PublicEndpoints` → `GuestTokenAuthenticationProvider` → `SecurityConfig`에 필터 등록 → Provider 테스트·`RoomControllerTest` 통과
   (실험: `setSuccessHandler(...)` 줄을 지우고 유효한 토큰으로 요청해 본다. 응답이 무엇으로 바뀌는가?)
6. `RoomMemberAuthorizationManager` → `SecurityConfig`에 `ROOM_SCOPED` 규칙 → 단위 테스트 통과
   (실험: `ROOM_SCOPED` 규칙을 `PublicEndpoints` 규칙보다 위로 올리면 어떤 테스트가 깨지는가?)
7. `SecurityIntegrationTest` → `bootRun` 후 curl로 토큰 없음·가짜·다른 방·같은 방 네 경우 확인

## 4. 왜 이렇게 했나 (다른 선택지)

| 결정 | 선택 | 안 고른 것 | 이유 |
| --- | --- | --- | --- |
| 인증 구조 | `AuthenticationFilter` + Converter + Provider | 직접 만든 필터, Resource Server introspection | [ADR 0005](../adr/0005-session-token-authentication.md) |
| 방 단위 권한 | 경로 규칙 + `AuthorizationManager` | 컨트롤러마다 if 문 | 하나만 빠뜨려도 다른 방을 조작할 수 있다. 앞으로 생길 API가 자동으로 보호된다 |
| 공개 경로의 토큰 | 헤더를 아예 보지 않음 | 표준대로 어디서든 401 | 세션이 방 하나에만 속해 만료된 토큰을 든 채 새 방에 들어가는 일이 흔하다 |
| 공개 경로 목록 | `PublicEndpoints` 하나를 인가·필터가 공유 | 두 곳에 따로 | 한쪽만 고치면 "열려 있는데 토큰 검사는 하는" 경로가 생긴다 |
| 헤더 없음 vs 형식 오류 | `null`(시도 안 함) vs 예외(401) | 둘 다 401 | 공개 경로·익명 흐름을 깨지 않으면서, 틀린 토큰은 분명히 알린다 |
| 401 응답 | `WWW-Authenticate: Bearer` (+ `invalid_token`) | 본문만 | RFC 6750. 토큰을 안 보낸 요청에는 오류 정보를 주지 않는다 |
| 필터 에러 형식 | `ProblemDetailSecurityErrorHandler` | 기본(본문 없는 403) | 필터 예외는 `@RestControllerAdvice`가 못 본다 |
| 세션 상태 | STATELESS + Redis | `HttpSession` | 서버가 여러 대여도 같은 토큰이 어디서나 통한다 |
| CSRF | 끔 | 켬 | 토큰이 쿠키가 아니라 헤더로 온다. 다른 사이트가 대신 보낼 수 없다 |
| 신원 객체 위치 | `global/security/ParticipantPrincipal` | `auth` 패키지 | 모든 도메인 컨트롤러가 쓴다. 도메인끼리 컨트롤러에서 참조하지 않게 한다 |
| 경로 매처 | `PathPatternRequestMatcher` | `AntPathRequestMatcher` | Spring Security 6.5에서 권장되는 쪽이고, `{code}` 경로 변수를 꺼내 준다 |

## 5. 스스로 답해볼 질문

1. 토큰 없이 보호된 경로에 오면 `RoomMemberAuthorizationManager`는 거부(false)를 돌려주는데 응답은 403이 아니라 401이다. 어느 클래스가, 무엇을 보고 401로 바꾸나?
2. 호스트가 방을 닫았다(`room.close()`, 아직 없음). 그 방 게스트의 토큰은 지금 설계에서 언제까지 통하나? 즉시 막으려면 어디를 바꿔야 하고, 왜 캐시를 두지 않은 결정이 여기서 도움이 되나?
3. M1 뒤쪽에서 STOMP(WebSocket)로 연결할 때도 같은 토큰을 쓴다. HTTP 필터 체인을 타지 않는 STOMP `CONNECT` 프레임에서 이번에 만든 클래스 중 무엇을 그대로 재사용할 수 있나?

## 6. 확인 명령

```bash
./gradlew test --tests '*Security*' --tests '*GuestToken*' --tests '*RoomMemberAuthorization*' --tests '*RoomControllerTest'
docker compose up -d && ./gradlew bootRun
# 방을 만들고 응답의 code·token 을 아래에 넣는다
curl -i localhost:8080/api/v1/rooms/<code>/queue                                # 401 + WWW-Authenticate: Bearer
curl -i localhost:8080/api/v1/rooms/<code>/queue -H 'Authorization: Bearer fake' # 401 + invalid_token
curl -i localhost:8080/api/v1/rooms/<code>/queue -H 'Authorization: Bearer <다른 방 token>'  # 403
curl -i localhost:8080/api/v1/rooms/<code>/queue -H 'Authorization: Bearer <token>'          # 404 (인가 통과, 큐 API 는 아직 없음)
```
