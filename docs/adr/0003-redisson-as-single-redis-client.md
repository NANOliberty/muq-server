# 0003. Redis 클라이언트는 Redisson 하나만 쓴다

- 날짜: 2026-10-10
- 상태: 채택됨

## 상황

뮤큐는 Redis를 큐(ZSET), 캐시, TTL, Pub/Sub, 분산락에 모두 쓴다 (`docs/plan.md` §6, §7.3).
Spring Boot의 기본 Redis 클라이언트는 Lettuce인데, Lettuce에는 분산락 구현이 없다.
스캐폴딩 단계에서 클라이언트를 정해야 이후 모든 Redis 코드가 같은 API 위에 쌓인다.

## 선택지

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. Lettuce만 (Spring Data Redis 기본) | 의존성이 가장 적고 Boot BOM이 버전을 관리 | 분산락을 `SET NX PX` + 해제 Lua로 직접 구현해야 함. 락 연장·재진입·대기를 직접 만들면 버그 나기 쉬움 |
| B. Lettuce + Redisson 둘 다 | 일반 명령은 익숙한 `RedisTemplate`, 락만 Redisson | 커넥션 풀이 두 벌. 설정·모니터링·장애 지점이 두 배 |
| C. Redisson 스타터 하나 | `RLock`(대기·보유 시간, watchdog) 제공. 스타터가 Spring Data Redis의 연결 팩토리까지 Redisson으로 바꿔 줘서 `RedisTemplate`·health 체크도 그대로 동작 | Boot BOM 밖이라 버전을 직접 맞춰야 함. Boot 메이저·마이너에 맞는 `redisson-spring-data-NN` 모듈이 필요 |

## 결정

**C를 선택한다.** 큐 변경에 필요한 락(대기 500ms, 보유 2s)을 검증된 구현으로 쓰고, 커넥션은 한 벌만 유지한다.
`redisson-spring-boot-starter:3.52.0`을 쓴다. 이 버전의 기본 모듈이 `redisson-spring-data-35`(Boot 3.5)이고, 실제로 Lettuce는 클래스패스에 들어오지 않는다.

## 결과

- Redis 접속 정보는 `spring.data.redis.*` 한 곳에서만 설정한다. Redisson 전용 설정 파일은 필요해질 때까지 만들지 않는다.
- 테스트의 `@ServiceConnection(name = "redis")`가 Redisson에도 적용되는 것을 확인했다 (`IntegrationTestSupport`).
- Redisson 3.52.0은 3.x 라인의 마지막 버전이고, 4.x는 Spring Boot 4 기준으로 나온다. Boot를 올릴 때는 Redisson도 반드시 같이 올린다.
- 재검토 신호: 3.x에서 보안 패치가 더 나오지 않는데 Boot 3.5에 머물러야 하는 경우. 그때는 4.x 스타터에서 기본 모듈을 빼고 `redisson-spring-data-35`를 직접 넣는 방법을 검토한다.
- ADR 번호 0002는 큐 동시성 전략(락 + Lua)용으로 비워 두었다 (ADR 0001과 README가 이미 그 번호를 가리킨다).
