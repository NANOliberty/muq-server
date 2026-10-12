# 02. 방 생성 API

> 한 줄 요약: 이 기능은 "겹치지 않는 6자리 코드의 방"을 DB 유니크 제약 + 트랜잭션 밖 재시도로 만들고, 만든 사람에게 익명 세션 토큰을 발급한다.

## 1. 요청이 흐르는 길

`POST /api/v1/rooms` `{ "nickname": "나노", "mode": "SPEAKER", "playerProvider": "YOUTUBE" }`

1. `RoomController.create()` — `@Valid`로 `CreateRoomRequest` 검증 → 서비스 호출 → 201 + `Location`
2. `RoomService.create()` — 호스트의 참여자 ID(UUID) 생성
3. `RoomService.saveWithUniqueCode()` — 최대 5회 반복: 코드 생성 → 트랜잭션 하나로 저장 시도
4. `RoomCodeGenerator.generate()` — `SecureRandom`으로 31종 문자 중 6자리
5. `RoomService.saveRoomAndHost()` — `RoomRepository.saveAndFlush()` + `RoomMemberRepository.save()` (PostgreSQL, 한 트랜잭션)
6. `GuestSessionService.issue()` — 256비트 토큰 생성
7. `GuestSessionRedisRepository.save()` — `guest:{token}` HASH + TTL을 MULTI/EXEC로 저장 (Redis)
8. `RoomService.toResponse()` — `CreateRoomResponse(room, session)` 조립

검증에 실패하면 2번에 가지 않고 `GlobalExceptionHandler`가 400 Problem Details를 만든다.

## 2. 핵심 코드 (발췌)

`RoomService` — 재시도 루프와 트랜잭션 경계.

```java
private Room saveWithUniqueCode(CreateRoomRequest request, UUID hostId) {
    for (int attempt = 1; attempt <= roomProperties.codeMaxAttempts(); attempt++) {
        String code = roomCodeGenerator.generate();
        try {
            // 왜 TransactionTemplate: 시도 한 번 = 트랜잭션 하나. 실패한 시도는 통째로 롤백되고 다음 시도는 새 트랜잭션에서 한다
            return transactionTemplate.execute(status -> saveRoomAndHost(code, request, hostId));
        } catch (DataIntegrityViolationException e) {
            if (!isCodeConflict(e)) {   // 왜: NOT NULL·길이 초과 같은 다른 위반까지 재시도하면 버그가 숨는다
                throw e;
            }
            log.warn("방 코드 충돌, 다시 시도한다. attempt={}", attempt);
        }
    }
    throw new BusinessException(ErrorCode.ROOM_CODE_EXHAUSTED);  // 왜 503: 요청 잘못이 아니라 다시 보내면 될 수 있는 실패
}
```

`GuestSessionRedisRepository` — HSET과 EXPIRE를 하나로.

```java
public void save(String token, GuestSession session, Duration ttl) {
    BatchOptions options = BatchOptions.defaults()
            .executionMode(BatchOptions.ExecutionMode.IN_MEMORY_ATOMIC);   // 왜: MULTI/EXEC 로 보낸다
    RBatch batch = redissonClient.createBatch(options);
    RMapAsync<String, String> map = batch.getMap(RedisKey.guestSession(token), StringCodec.INSTANCE);
    map.putAllAsync(toFields(session));   // HSET
    map.expireAsync(ttl);                 // 왜 같은 배치: 따로 보내면 사이에 죽었을 때 TTL 없는 세션이 영원히 남는다
    batch.execute();
}
```

## 3. 따라치기 순서

1. enum 4개(`RoomMode`, `PlayerProvider`, `RoomStatus`, `MemberRole`) → 컴파일
2. `CreateRoomRequest`, `CreateRoomResponse` → 컴파일
3. `RoomService`(시그니처만) → `RoomController` → `RoomControllerTest` 통과 (서비스는 목)
4. `V1__create_room.sql`, `V2__create_room_member.sql` → `Room`, `RoomMember` → `HealthCheckTest` 통과 (스키마 검증)
5. `RoomRepository`, `RoomMemberRepository` → `RoomRepositoryTest` 통과 (같은 코드 두 번 저장 시 예외)
6. `RedisKey` → `GuestSession` → `GuestSessionRedisRepository` → `GuestSessionRedisRepositoryTest` 통과
7. `GuestSessionService` → `GuestSessionServiceTest` 통과
8. `RoomProperties`(+ `application.yml`의 `muq.room`, `@ConfigurationPropertiesScan`), `ClockConfig`, `RoomCodeGenerator`
9. `ErrorCode.ROOM_CODE_EXHAUSTED`, `Room.CODE_UNIQUE_CONSTRAINT` → `RoomService` 본문
   → `bootRun` 후 curl로 201 확인, `redis-cli hgetall guest:...`로 세션 확인
10. `RoomCodeGeneratorTest`, `RoomServiceTest`, `RoomCreateApiTest` 통과
    (실험: `RoomService.create()`에 `@Transactional`을 붙이고 `RoomServiceTest`를 돌려 본다. 어떤 테스트가 어떤 예외로 깨지는가?)

## 4. 왜 이렇게 했나 (다른 선택지)

| 결정 | 선택 | 안 고른 것 | 이유 |
| --- | --- | --- | --- |
| 호스트 식별 | 익명 세션 + 참여자 ID | User + JWT, 임시 헤더 | [ADR 0004](../adr/0004-anonymous-participant-session.md) |
| 코드 중복 방지 | DB 유니크 제약 + 재시도 | `existsByCode` 조회 후 저장 | 조회와 저장 사이에 다른 요청이 같은 코드를 넣을 수 있다. 제약만이 동시 요청을 막는다 |
| 트랜잭션 경계 | `TransactionTemplate`으로 시도마다 | `create()`에 `@Transactional` | 제약 위반이 나면 그 트랜잭션은 롤백 전용이 되어 재시도할 수 없다 |
| 재시도 조건 | 제약 이름이 `uk_room_code`일 때만 | `DataIntegrityViolationException` 전부 | 다른 위반을 코드 충돌로 오해하면 원인이 숨는다 |
| 저장 순서 | DB 커밋 → Redis 세션 | 세션 먼저, 또는 둘을 묶기 | 세션에 확정된 코드가 필요하다. 실패 시 남는 방은 만료로 정리된다 |
| 세션 토큰 | 불투명 토큰 256비트 | JWT | Redis 키를 지우면 즉시 무효화된다 |
| 세션 저장 | HSET + EXPIRE를 MULTI/EXEC | 명령 두 번 | TTL 없는 키가 남는 경우를 없앤다 |
| 코드 문자 | 31종 (`0 O 1 I L` 제외) | 영숫자 36종 | 사람이 보고 입력하는 값이다 |
| 현재 시각 | `Clock` 빈 주입 | `Instant.now()` | 테스트에서 만료 시각을 고정값으로 검증한다 |
| 큐 저장소 | (참고) Redis ZSET | DB 테이블 | [ADR 0001](../adr/0001-queue-in-redis-sorted-set.md) |

## 5. 스스로 답해볼 질문

1. `RoomService` 안에 `@Transactional`이 붙은 private 메서드를 만들어 `create()`에서 호출하면 왜 트랜잭션이 걸리지 않나? `TransactionTemplate`은 이 문제를 어떻게 피하나?
2. 방과 멤버는 저장됐는데 Redis가 죽어 세션 발급이 실패하면, 클라이언트는 무엇을 받고 DB에는 무엇이 남나? 클라이언트가 같은 요청을 다시 보내면 어떻게 되나?
3. 서버가 2대가 되어 두 서버가 같은 순간에 같은 코드를 뽑으면 어디에서 막히나? 이 설계에 서버 대수에 따라 달라지는 부분이 있나?

## 6. 확인 명령

```bash
./gradlew test --tests '*Room*' --tests '*GuestSession*'
docker compose up -d && ./gradlew bootRun
curl -i -X POST localhost:8080/api/v1/rooms -H 'Content-Type: application/json' \
  -d '{"nickname":"나노","mode":"SPEAKER","playerProvider":"YOUTUBE"}'
docker exec muq-redis redis-cli --scan --pattern 'guest:*'
docker exec muq-postgres psql -U muq -d muq -c 'select code, status, expires_at from room'
```
