# 뮤큐 (MuQ)

서로 다른 음악 앱을 쓰는 친구들이 **공유 큐를 함께 채우고**, 친구가 지금 듣는 곡을 **내 플레이어로 따라 듣는** 서비스의 백엔드.

> 상태: M0 (기반 구축). 기획서는 [docs/plan.md](docs/plan.md).

## 왜 만들었나

음악 공유가 "링크 보내기"에서 끝난다. 상대가 쓰는 앱이 다르면 링크를 눌러도 재생이 안 된다. Spotify Jam이나 디스코드 함께 듣기는 같은 플랫폼 유료 구독자끼리만 된다. 뮤큐는 **플랫폼 간 곡 매칭**과 **재생 동기화**를 한 서비스에 담는다.

## 핵심 기술 과제

| 과제 | 접근 | 문서 |
| --- | --- | --- |
| 플랫폼 간 같은 곡 찾기 | ISRC → 정규화 검색 → 수동 교정 3단계 폴백, Redis 캐시 | docs/learning/ |
| 떨어진 기기 재생 위치 맞추기 | 서버 시각 기준 오프셋 보정, 300ms 임계 seek | docs/learning/ |
| 동시 큐 변경 | 방 단위 Redisson 락 + 투표는 Lua 원자 스크립트 | docs/adr/0002 |

## 스택

Java 21 · Spring Boot 3.5 · PostgreSQL · Redis(Redisson) · WebSocket/STOMP · Testcontainers · GitHub Actions · Oracle Cloud

## 실행

```bash
cp .env.example .env
docker compose up -d        # Postgres, Redis, WireMock(외부 API 목)
./gradlew bootRun
open http://localhost:8080/swagger-ui.html
```

## 문서

- `docs/plan.md` — 기획서
- `docs/adr/` — 설계 결정 기록
- `docs/learning/` — 기능별 학습 노트 (요청 흐름, 따라치기 순서)
