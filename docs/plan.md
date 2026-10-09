# 뮤큐(MuQ) 서비스 기획서 (요약본)

> 전체 기획서(다이어그램 포함): https://claude.ai/code/artifact/754072ac-104d-46f8-bd7d-b547774b30ac
> 이 파일은 Claude Code가 읽기 위한 요약본이다. 결정이 바뀌면 여기도 같이 고친다.

## 1. 한 줄 정의

친구가 지금 듣는 곡을 내 플레이어로 바로 따라 듣고, 한 공간에서는 모두의 폰으로 큐를 함께 채우는 음악 동행 서비스. 서비스는 음원을 재생·전송하지 않는다. 각 사용자의 플레이어가 자기 계정으로 재생하고, 서버는 **곡 매칭**과 **상태 동기화**만 한다.

## 2. 핵심 시나리오

- **A. 공유 큐 (한 공간)**: 호스트가 방 생성 → 참여자가 QR/코드로 입장해 곡 추가·투표 → 큐 변경이 전원에게 즉시 반영 → 곡 끝나면 호스트 플레이어가 큐 1순위 자동 재생.
- **B. 따라 듣기 (떨어진 곳)**: 친구의 "지금 듣는 곡"을 보고 누르면 내 플레이어에서 같은 곡 재생. 같은 플랫폼이면 재생 위치까지 맞춰 같이 듣기.
- **C. 링크 모드 (국내 앱)**: FLO·멜론 공유 링크를 붙여넣으면 서버가 제목·아티스트를 뽑아 Spotify/YouTube 곡으로 매칭. 동기화는 제외.

## 3. 기능 모듈

| 모듈 | 기능 | 우선순위 |
| --- | --- | --- |
| 계정·연결 | 소셜 로그인, 플레이어 연결(Spotify OAuth / YouTube / Apple Music), 친구, **게스트 입장**(닉네임만) | P0 |
| 공유 큐(방) | 방 생성·입장, **스피커 모드 / 각자 듣기 모드**, 곡 추가, 투표 정렬, 호스트 제어, 자동 만료 | P0 |
| 곡 매칭 | ISRC → 정규화 검색 → 수동 교정 3단계, 캐시, 신뢰도 | P0 |
| 따라 듣기 | 친구 현재 곡 조회, 내 플레이어 재생, 위치 동기화, **리슨 링크**(24h, 비친구 허용) | P1 |
| 링크 모드 | FLO·멜론·지니 링크 파싱 | P1 |
| 리액션 | 재생 중 곡에 이모지 반응, 실시간 표시 | P1 |
| 기록 | 재생 이력, 플레이리스트 내보내기 | P2 |

**규칙**
- 큐 순서 = 투표 수 내림차순, 동률이면 추가 시각 오름차순. 곡당 1표, 자기 곡 투표 불가.
- 같은 곡(Track ID)은 큐에 한 번만. 이미 있으면 "추가"를 "투표"로 처리.
- 방은 마지막 활동 후 6시간 뒤 만료. 코드는 6자리 영숫자.
- 게스트는 추가·투표·리액션 가능. 따라 듣기·플레이어 연결은 로그인 필요.
- 따라 듣기 기본은 친구 공개. 리슨 링크는 24h TTL, 취소 가능, 전체 공개는 옵션(기본 꺼짐).
- 매칭 실패 시 후보 3개 제시 → 선택 결과는 MANUAL로 저장해 전체 사용자에 적용.

## 4. 플랫폼 지원

| 플랫폼 | 현재 곡 읽기 | 재생 | 검색 | 조건 | 수준 |
| --- | --- | --- | --- | --- | --- |
| Spotify | Web API | Web Playback SDK | Web API | 재생은 Premium | 정식 (1차) |
| YouTube | 불가 | IFrame Player | Data API v3 | 무료 | 정식 (1차) |
| Apple Music | MusicKit (iOS) | MusicKit | Apple Music API | iOS 앱 + 구독 | 정식 (2차) |
| FLO·멜론·지니·바이브 | 공개 API 없음 | — | — | — | 링크 모드만 |

1차 범위는 Spotify ↔ YouTube. 둘 다 웹에서 끝나고, YouTube는 무료라 시연이 쉽다.

## 5. 아키텍처

클라이언트(React 웹 / iOS) ↔ Spring Boot 단일 서버(REST + WebSocket/STOMP + 매칭 + 동기화) ↔ Redis(큐 ZSET·캐시·TTL·Pub/Sub·락) / PostgreSQL / 외부 음원 API.
큐 변경은 WebSocket → Redis Pub/Sub → 같은 방의 모든 연결로 전달. 서버를 늘려도 동기화가 깨지지 않는다. 모놀리스로 시작하고 패키지로만 도메인을 나눈다.

## 6. 데이터

**PostgreSQL**: User, ProviderAccount(토큰 AES-256 암호화), Friendship, Track(서비스 내부 곡 ID, isrc nullable), TrackMapping(provider, providerTrackId, confidence, source=ISRC/SEARCH/MANUAL), Room(code, mode, hostId, playerProvider, status, expiresAt), RoomMember, PlayHistory.

**Redis**

| 키 | 구조 | 용도 |
| --- | --- | --- |
| room:{code}:queue | ZSET (score = votes×10¹² − addedAtMs) | 큐 순서 |
| room:{code}:votes:{trackId} | SET(userId) | 중복 투표 방지 |
| room:{code}:now | HASH(trackId, positionMs, serverTs, state) | 재생 상태 |
| room:{code}:lock | STRING + TTL | 큐 변경 분산락 |
| room:{code}:reactions:{trackId} | HASH(emoji→count) | 리액션 |
| guest:{token} | HASH + TTL | 게스트 세션 |
| user:{id}:now | HASH + TTL 30s | 현재 듣는 곡 |
| listen-link:{token} | STRING(userId) + TTL 24h | 리슨 링크 |
| match:{provider}:{providerTrackId} | STRING(trackId) + TTL 7d | 매칭 캐시 |
| search:{provider}:{hash(q)} | STRING(JSON) + TTL 1h | 검색 캐시 |
| 채널 room.{code} | Pub/Sub | 브로드캐스트 |

## 7. 핵심 기술 과제

**7.1 곡 매칭** — ① ISRC 일치면 confidence 1.0 ② 제목 정규화(괄호·feat·Remaster 제거) + 아티스트로 검색, 재생시간 ±3초 이내 선택(0.6~0.9) ③ 수동 교정(MANUAL, 1.0). 결과는 TrackMapping 영속 + Redis 7일.

**7.2 재생 동기화** — 서버가 `{trackId, positionMs, serverTs, state}` 브로드캐스트. 클라는 입장 시 NTP식 왕복 3회로 오프셋 측정. 목표 위치 = positionMs + (now − serverTs − offset). 300ms 이상 차이일 때만 seek. 호스트가 10초마다 하트비트. YouTube는 전원 `ready` 후 서버가 `start`.

**7.3 동시성** — 큐 변경(추가·삭제·스킵)은 Redisson RLock(대기 500ms, 보유 2s). 투표는 락 없이 Lua 스크립트 하나로 원자 처리. 검증: 동시 50명 같은 곡 추가·투표 → 큐 중복 0건.

**7.4 외부 API** — Resilience4j 서킷브레이커, 실패 시 캐시 또는 "나중에 매칭" 상태. 401 시 리프레시 1회, 실패하면 `disconnected` 표시.

## 8. 인프라 (무료)

Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Redis 7(Redisson) · STOMP · Flyway · Resilience4j · springdoc · Testcontainers · WireMock · Docker · GitHub Actions → GHCR → SSH 배포 · **Oracle Cloud Always Free**(상시 서버) · AWS 프리티어는 S3·IAM·CloudWatch만 · Prometheus+Grafana · 프론트 React+Vite(Vercel) · iOS SwiftUI+MusicKit(2차).

## 9. API

REST `/api/v1`: auth/{provider}/callback, me, me/visibility, me/listen-link, rooms, rooms/{code}/join|guest, rooms/{code}, rooms/{code}/tracks, tracks/{trackId}/vote, reactions, player/{action}, lock, tracks/search, tracks/{id}/resolve, tracks/{id}/mapping, friends, listen/{token}, friends/{id}/listen-along, rooms/{code}/history.
STOMP 구독: /topic/rooms/{code}/queue|player|members, /user/queue/listen-along. 발행: /app/rooms/{code}/heartbeat|ready, /app/me/now-playing.
에러는 RFC 9457 Problem Details. 쓰기 요청은 Idempotency-Key(Redis 10분).

## 10. 로드맵

| 단계 | 기간 | 내용 | 완료 기준 |
| --- | --- | --- | --- |
| M0 기반 | 1주 | 스캐폴딩, CLAUDE.md, compose, CI | health 200, CI 통과 |
| M1 공유 큐 | 2주 | 방·큐·투표·WS, YouTube 플레이어 | 브라우저 2개 1초 내 동기화, 자동 다음 곡 |
| M2 곡 매칭 | 2주 | Spotify 연동, ISRC·검색·캐시 | 샘플 50곡 90% 이상 |
| M3 따라 듣기 | 2주 | 친구·현재 곡·위치 동기화·링크 | 다른 네트워크 두 기기 ±500ms |
| M4 운영 마감 | 1~2주 | 배포·모니터링·부하 테스트·README | 동시 50명 큐 중복 0건 |

## 11. 비범위

음원 호스팅·다운로드 / 안드로이드(PWA로 대체) / 추천·취향 분석 / 공개 방 탐색 / 텍스트 채팅 / 수익화.
