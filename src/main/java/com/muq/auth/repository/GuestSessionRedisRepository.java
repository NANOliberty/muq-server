package com.muq.auth.repository;

import com.muq.auth.domain.GuestSession;
import com.muq.global.redis.RedisKey;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.redisson.api.BatchOptions;
import org.redisson.api.RBatch;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Repository;

/**
 * 게스트 세션의 Redis 저장소. 키는 guest:{token}, 구조는 HASH + TTL 이다.
 * 게스트 세션에 대한 Redis 명령은 이 클래스에만 둔다.
 */
@Repository
@RequiredArgsConstructor
public class GuestSessionRedisRepository {

    private static final String PARTICIPANT_ID = "participantId";
    private static final String NICKNAME = "nickname";
    private static final String ROOM_CODE = "roomCode";

    private final RedissonClient redissonClient;

    // 왜 배치(MULTI/EXEC): HSET 과 EXPIRE 는 별개의 명령이다. 따로 보내면 그 사이에 서버가 죽었을 때
    //   TTL 없는 세션이 영원히 남는다. 한 트랜잭션으로 묶어 "둘 다 되거나 둘 다 안 되거나"로 만든다.
    public void save(String token, GuestSession session, Duration ttl) {
        BatchOptions options = BatchOptions.defaults()
                .executionMode(BatchOptions.ExecutionMode.IN_MEMORY_ATOMIC);
        RBatch batch = redissonClient.createBatch(options);
        RMapAsync<String, String> map = batch.getMap(RedisKey.guestSession(token), StringCodec.INSTANCE);
        map.putAllAsync(toFields(session));
        map.expireAsync(ttl);
        batch.execute();
    }

    public Optional<GuestSession> findByToken(String token) {
        // 왜 StringCodec: Redisson 기본 코덱은 바이너리로 직렬화한다. 문자열로 두면 redis-cli 에서 그대로 읽힌다.
        RMap<String, String> map = redissonClient.getMap(RedisKey.guestSession(token), StringCodec.INSTANCE);
        Map<String, String> fields = map.readAllMap();
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toSession(fields));
    }

    private Map<String, String> toFields(GuestSession session) {
        return Map.of(
                PARTICIPANT_ID, session.participantId().toString(),
                NICKNAME, session.nickname(),
                ROOM_CODE, session.roomCode());
    }

    private GuestSession toSession(Map<String, String> fields) {
        return new GuestSession(
                UUID.fromString(fields.get(PARTICIPANT_ID)),
                fields.get(NICKNAME),
                fields.get(ROOM_CODE));
    }
}
