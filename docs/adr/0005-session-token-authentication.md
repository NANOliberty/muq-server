# 0005. 세션 토큰 인증은 Spring Security의 AuthenticationFilter + 우리 Provider로 한다

- 날짜: 2026-11-13
- 상태: 채택됨

## 상황

방 생성·게스트 입장이 불투명 세션 토큰(`guest:{token}`, ADR 0004)을 발급하지만, 그 토큰을 검증하는 쪽이 없었다.
곡 추가·투표·재생 같은 다음 API는 전부 "누가, 어느 방에서" 보냈는지 알아야 한다. 같은 인증을 M1 뒤쪽의 STOMP 연결과 M2의 로그인(JWT)도 써야 하므로, 처음 들어오는 지금 구조를 정해야 한다.

## 선택지

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. `HandlerMethodArgumentResolver`로 컨트롤러 인자에서 토큰 해석 (Security 없음) | 가장 단순. 의존성 없음 | 인증이 컨트롤러에 도달한 뒤에야 일어남. 방 단위 권한을 API마다 직접 확인해야 하고, STOMP·JWT와 공유할 틀이 없음 |
| B. 직접 만든 `OncePerRequestFilter`가 Redis를 조회해 `SecurityContext`를 채움 | 흐름이 한 클래스에 보임 | 인증 로직이 필터에 묶여 STOMP에서 재사용하려면 꺼내야 함. JWT가 들어오면 필터 안에서 분기가 늘어남 |
| C. `AuthenticationFilter` + `AuthenticationConverter` + 우리 `AuthenticationProvider` (Spring Security 표준 골격) | 헤더 해석(Converter)과 검증(Provider)이 분리됨. JWT는 `ProviderManager`에 Provider 하나 추가로 끝나고, STOMP 연결은 같은 Provider를 직접 부름 | 클래스가 여러 개로 나뉨. 성공 처리기 등 로그인 폼용 기본값을 직접 꺼야 함 |
| D. OAuth2 Resource Server의 opaque token introspection | Bearer 헤더 처리·RFC 6750 에러를 프레임워크가 해 줌 | 원격 인가 서버에 토큰을 물어보는 개념이라 Redis 조회와 맞지 않음. `oauth2-resource-server` 의존성이 들어오고 JWT와 섞을 때 `AuthenticationManagerResolver`가 필요해짐 |

## 결정

**C를 선택한다.** 인증 로직을 `GuestTokenAuthenticationProvider` 한 곳에 두고, HTTP 필터·STOMP·JWT가 같은 `AuthenticationManager` 틀을 공유한다.

세부 결정:

- **방 단위 권한**은 컨트롤러가 아니라 경로 규칙 `/api/v1/rooms/{code}/**` + `RoomMemberAuthorizationManager` 한 곳에서 건다. 앞으로 생길 큐·투표·재생 API는 규칙을 다시 쓰지 않아도 보호된다.
- **공개 경로**는 `PublicEndpoints` 한 목록을 인가 규칙과 인증 필터가 함께 쓴다. 공개 경로에서는 `Authorization` 헤더를 아예 보지 않는다. 세션이 방 하나에만 속해서, 지난 방의 만료된 토큰을 든 채 새 방을 만들거나 입장하는 일이 흔하기 때문이다 (NANO 결정). 표준(어디서든 401)보다 이 서비스의 사용 흐름에 맞춘 것이다.
- **401·403**은 필터에서 나므로 `ProblemDetailSecurityErrorHandler`가 다른 API와 같은 Problem Details로 쓴다. 401에는 RFC 6750대로 `WWW-Authenticate: Bearer`를 붙이고, 토큰을 보냈는데 틀린 경우에만 `error="invalid_token"`을 더한다.
- **세션은 요청마다 Redis에서 조회한다.** 캐시를 두면 만료·강퇴가 즉시 반영되지 않는다.
- **STATELESS, CSRF 끔.** 상태는 Redis에 있고, 토큰은 쿠키가 아니라 헤더로 오므로 다른 사이트가 대신 보낼 수 없다.

## 결과

- `ParticipantPrincipal`(`global/security`)이 방 안의 신원이다. 컨트롤러는 `@AuthenticationPrincipal`로 받고, auth 도메인 클래스를 직접 참조하지 않는다.
- 보안 설정은 `List<AuthenticationProvider>`를 받는다. M2에서 JWT Provider 빈을 추가하면 설정 변경 없이 같은 `ProviderManager`에 들어온다.
- 403은 지금 모두 `ROOM_ACCESS_DENIED`다. 호스트 전용 규칙(스킵·잠금)이 생기면 `ProblemDetailSecurityErrorHandler`에서 둘을 구분해야 한다.
- Redis 장애 시 401이 아니라 500이 나지만, 필터에서 나는 예외라 Boot 기본 에러 형식으로 나간다. 운영 마감(M4)에서 다룬다.
- `@WebMvcTest`는 우리 `SecurityConfig`를 자동으로 읽지 않는다. 보안 계약까지 검증할 슬라이스 테스트는 `SecurityConfig`·에러 핸들러·Provider를 `@Import`하고 세션 저장소를 목으로 둔다.
- 재검토 신호: 요청마다의 Redis 조회가 지연의 눈에 띄는 비중이 되면(부하 테스트, M4) 짧은 TTL의 로컬 캐시와 "강퇴 시 무효화 이벤트"를 함께 검토한다.
