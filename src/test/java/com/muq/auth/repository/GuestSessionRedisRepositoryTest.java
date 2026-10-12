package com.muq.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.muq.auth.domain.GuestSession;
import com.muq.global.redis.RedisKey;
import com.muq.support.IntegrationTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 게스트 세션이 실제 Redis 에 HASH + TTL 로 저장되고, TTL 이 지나면 사라지는지 검증한다.
 * 토큰은 테스트마다 새로 만들어서 공유 컨테이너의 다른 테스트와 키가 겹치지 않는다.
 */
class GuestSessionRedisRepositoryTest extends IntegrationTestSupport {

    private static final Duration SIX_HOURS = Duration.ofHours(6);

    @Autowired
    private GuestSessionRedisRepository repository;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    @DisplayName("저장한 세션을 토큰으로 다시 찾을 수 있다")
    void saveAndFind() {
        String token = newToken();
        GuestSession session = new GuestSession(UUID.randomUUID(), "나노", "K7M2QX");

        repository.save(token, session, SIX_HOURS);

        assertThat(repository.findByToken(token)).contains(session);
    }

    @Test
    @DisplayName("세션 키에는 지정한 TTL이 걸린다")
    void ttlIsSet() {
        String token = newToken();

        repository.save(token, new GuestSession(UUID.randomUUID(), "나노", "K7M2QX"), SIX_HOURS);

        long remainMs = redissonClient.getMap(RedisKey.guestSession(token), StringCodec.INSTANCE)
                .remainTimeToLive();
        assertThat(remainMs).isPositive();
        assertThat(remainMs).isLessThanOrEqualTo(SIX_HOURS.toMillis());
    }

    @Test
    @DisplayName("TTL이 지나면 세션은 사라진다")
    void expires() {
        String token = newToken();

        repository.save(token, new GuestSession(UUID.randomUUID(), "나노", "K7M2QX"), Duration.ofMillis(200));

        await().atMost(Duration.ofSeconds(3))
                .until(() -> repository.findByToken(token).isEmpty());
    }

    @Test
    @DisplayName("없는 토큰으로 찾으면 비어 있다")
    void unknownToken() {
        assertThat(repository.findByToken(newToken())).isEmpty();
    }

    private String newToken() {
        return UUID.randomUUID().toString();
    }
}
