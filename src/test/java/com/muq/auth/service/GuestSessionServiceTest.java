package com.muq.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muq.auth.domain.GuestSession;
import com.muq.auth.repository.GuestSessionRedisRepository;
import com.muq.support.IntegrationTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 세션 발급이 "토큰을 만들고, 그 토큰으로 찾을 수 있게 저장한다"를 지키는지 검증한다.
 * 저장소를 목으로 바꾸지 않고 실제 Redis 까지 다녀온다. 서비스가 얇아서 목으로는 검증할 것이 남지 않는다.
 */
class GuestSessionServiceTest extends IntegrationTestSupport {

    private static final Duration TTL = Duration.ofHours(6);

    @Autowired
    private GuestSessionService guestSessionService;

    @Autowired
    private GuestSessionRedisRepository guestSessionRedisRepository;

    @Test
    @DisplayName("발급한 토큰으로 참여자·닉네임·방 코드를 찾을 수 있다")
    void issue() {
        UUID participantId = UUID.randomUUID();

        String token = guestSessionService.issue(participantId, "나노", "K7M2QX", TTL);

        assertThat(guestSessionRedisRepository.findByToken(token))
                .contains(new GuestSession(participantId, "나노", "K7M2QX"));
    }

    @Test
    @DisplayName("토큰은 256비트 난수의 URL-safe Base64 문자열(43자)이다")
    void tokenFormat() {
        String token = guestSessionService.issue(UUID.randomUUID(), "나노", "K7M2QX", TTL);

        assertThat(token).hasSize(43);
        assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("같은 참여자에게 다시 발급해도 토큰은 매번 다르다")
    void tokensAreUnique() {
        UUID participantId = UUID.randomUUID();

        String first = guestSessionService.issue(participantId, "나노", "K7M2QX", TTL);
        String second = guestSessionService.issue(participantId, "나노", "K7M2QX", TTL);

        assertThat(first).isNotEqualTo(second);
    }
}
