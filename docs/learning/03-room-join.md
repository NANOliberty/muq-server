# 03. 방 조회·게스트 입장

> 한 줄 요약: 이 기능은 "코드로 방을 찾고 닉네임만으로 들어온다"를, 열려 있는 방인지 한 곳에서 판단하고 세션 수명을 방에 맞추는 것으로 해결한다.

## 1. 요청이 흐르는 길

**조회** `GET /api/v1/rooms/k7m2qx`

1. `RoomController.get()` — 경로의 코드를 그대로 서비스에 넘김
2. `RoomService.get()` → `findOpenRoom(code, now)`
3. `findOpenRoom()` — 코드를 대문자로 맞춤 → `RoomRepository.findByCode()` → 없으면 `ROOM_NOT_FOUND`(404)
4. `Room.isOpen(now)` — 거짓이면 `ROOM_CLOSED`(410)
5. `RoomResponse.from(room)` — 방의 기본 정보만 담아 200

**입장** `POST /api/v1/rooms/K7M2QX/guest` `{ "nickname": "민수" }`

1. `RoomController.joinAsGuest()` — `@Valid JoinRoomRequest` 검증 → 서비스 호출 → 201
2. `RoomService.joinAsGuest()` → `findOpenRoom(code, now)` (조회와 같은 관문)
3. 참여자 ID(UUID) 생성 → `RoomMemberRepository.save(RoomMember.guest(...))` (PostgreSQL)
4. `GuestSessionService.issue(..., room.remainingLifetime(now))` → `guest:{token}` (Redis)
5. `RoomEntryResponse(room, session)` — 방 생성 응답과 같은 모양

## 2. 핵심 코드 (발췌)

`Room` — 열려 있는지는 엔티티가 판단한다.

```java
public boolean isOpen(Instant now) {
    // 왜 status 만 보지 않나: 만료 시각이 지나도 status 를 바꿔 주는 배치가 없다. 시각을 직접 비교해야 항상 정확하다
    return status == RoomStatus.ACTIVE && now.isBefore(expiresAt);   // 왜 isBefore: 만료 정각은 이미 끝난 것
}
```

`RoomService` — 조회와 입장이 함께 지나는 관문.

```java
private Room findOpenRoom(String code, Instant now) {
    String normalizedCode = code.toUpperCase(Locale.ROOT);            // 왜: 사람이 손으로 입력한다. 저장된 코드는 항상 대문자
    Room room = roomRepository.findByCode(normalizedCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));   // 404: 코드 오타
    if (!room.isOpen(now)) {
        throw new BusinessException(ErrorCode.ROOM_CLOSED);           // 410: 있었지만 끝난 방
    }
    return room;
}

public RoomEntryResponse joinAsGuest(String code, JoinRoomRequest request) {
    Instant now = clock.instant();                                    // 왜 한 번만: 열림 판단과 TTL 계산이 같은 시각을 써야 한다
    Room room = findOpenRoom(code, now);
    UUID participantId = UUID.randomUUID();
    roomMemberRepository.save(RoomMember.guest(room, participantId, request.nickname(), now));
    String token = guestSessionService.issue(
            participantId, request.nickname(), room.getCode(), room.remainingLifetime(now));  // 왜: 세션이 방보다 오래 살지 않게
    return toEntry(room, token, participantId, request.nickname(), MemberRole.GUEST);
}
```

## 3. 따라치기 순서

1. `RoomResponse`, `SessionResponse`, `RoomEntryResponse` 만들고 `CreateRoomResponse` 삭제
   → 기존 테스트가 타입 이름만 바꿔 그대로 통과 (리팩터는 동작을 바꾸지 않는다)
2. `ErrorCode`에 `ROOM_NOT_FOUND`, `ROOM_CLOSED` → `JoinRoomRequest` → `RoomService` 시그니처 2개
   → `RoomController.get()`, `joinAsGuest()` → `RoomControllerTest` 통과 (서비스는 목)
3. `Room.isOpen()`, `remainingLifetime()` → `RoomTest` 통과 (스프링 없이)
   (실험: `isBefore`를 `!isAfter`로 바꾸면 어떤 테스트가 깨지는가?)
4. `RoomMember.guest()`, `RoomRepository.findByCode()` → `RoomRepositoryTest` 통과
5. `RoomService.findOpenRoom()` → `get()` → `joinAsGuest()` 본문
   → `bootRun` 후 방 생성 → 소문자 코드로 조회 → 입장, `redis-cli ttl guest:...`가 방의 남은 초와 같은지 확인
6. `RoomServiceTest`에 케이스 추가, `RoomJoinApiTest` 작성 → 통과
   (실험: `joinAsGuest`의 TTL을 `roomProperties.ttl()`로 바꾸면 어떤 테스트가 깨지는가?)

## 4. 왜 이렇게 했나 (다른 선택지)

| 결정 | 선택 | 안 고른 것 | 이유 |
| --- | --- | --- | --- |
| 조회의 인증 | 없음 | 토큰 필요 | 입장 전 화면이 방 정보를 보여 줘야 한다. 응답에는 호스트 ID·멤버·큐를 넣지 않는다 |
| 인증 코드 도입 시점 | 이 기능에 넣지 않음 | Spring Security를 지금 | 두 API 모두 토큰이 없는 사람이 부른다. 토큰을 요구하는 첫 API(곡 추가) 직전에 따로 만든다 |
| 없는 방 / 끝난 방 | 404 / 410 | 둘 다 404 | 클라이언트가 "코드 오타"와 "종료된 방"을 다르게 안내한다 |
| 만료 판단 | 조회 시점에 시각 비교 | 배치가 `status`를 바꾸길 기다림 | 배치 주기만큼 틀린 답을 주게 된다. 배치가 없어도 정확하다 |
| 판단 위치 | `Room.isOpen()` | 서비스에서 if 문 | 규칙이 한 곳에 있어야 조회·입장·이후 기능이 같은 답을 낸다. DB 없이 테스트된다 |
| 대소문자 | 서비스에서 대문자로 맞춤 | DB에서 `lower()` 비교 | 함수 비교는 `uk_room_code` 인덱스를 못 탄다. 입력을 맞추면 인덱스를 그대로 쓴다 |
| 게스트 세션 TTL | 방의 남은 수명 | 새로 6시간 | 방이 끝난 뒤에도 유효한 토큰이 남지 않게 한다 |
| 입장의 트랜잭션 | `@Transactional` 없음 | 메서드 전체에 | DB 쓰기가 INSERT 하나다. Redis 호출까지 트랜잭션 안에 넣으면 커넥션만 오래 쥔다 |
| 응답 DTO | `RoomEntryResponse`를 생성·입장이 공유 | 기능마다 따로 | "방에 들어왔다"는 같은 사건이다. 클라이언트가 한 가지로 처리한다 |
| 참여자 식별 | 익명 세션 + 참여자 ID | — | [ADR 0004](../adr/0004-anonymous-participant-session.md) |

## 5. 스스로 답해볼 질문

1. `findOpenRoom()`이 방을 찾은 직후, 멤버를 저장하기 직전에 방의 만료 시각이 지나면 무슨 일이 생기나? 그 결과는 받아들일 만한가, 막으려면 무엇이 필요한가?
2. 멤버는 DB에 저장됐는데 세션 발급(Redis)이 실패하면 사용자는 무엇을 보나? 같은 닉네임으로 다시 입장하면 `room_member`에는 행이 몇 개 남나?
3. 조회 API가 인증 없이 열려 있으면 누군가 코드를 무작위로 대입해 볼 수 있다. 활성 방이 1,000개일 때 한 번의 시도가 맞을 확률은 얼마인가? 서버가 여러 대일 때 요청 횟수 제한은 어디에 상태를 두어야 하나?

## 6. 확인 명령

```bash
./gradlew test --tests '*Room*'
docker compose up -d && ./gradlew bootRun
curl -s -X POST localhost:8080/api/v1/rooms -H 'Content-Type: application/json' \
  -d '{"nickname":"나노","mode":"SPEAKER","playerProvider":"YOUTUBE"}'      # 응답의 code 를 아래에 넣는다
curl -i localhost:8080/api/v1/rooms/<code 소문자>
curl -i -X POST localhost:8080/api/v1/rooms/<code>/guest -H 'Content-Type: application/json' -d '{"nickname":"민수"}'
curl -i localhost:8080/api/v1/rooms/ZZZZZZ                                   # 404 ROOM_NOT_FOUND
```
