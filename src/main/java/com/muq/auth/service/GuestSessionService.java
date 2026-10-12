package com.muq.auth.service;

import com.muq.auth.domain.GuestSession;
import com.muq.auth.repository.GuestSessionRedisRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게스트 세션을 발급한다.
 * 다른 도메인(room)은 이 서비스를 통해서만 세션을 만든다. 토큰을 어떻게 만들고 어디에 저장하는지는 여기서 끝난다.
 */
@Service
@RequiredArgsConstructor
public class GuestSessionService {

    private static final int TOKEN_BYTES = 32;

    private final GuestSessionRedisRepository guestSessionRedisRepository;

    // 왜 SecureRandom: 토큰은 곧 비밀번호다. Random 이나 UUID.randomUUID() 의 예측 가능성에 기대지 않고
    //   암호학적 난수 256비트를 쓴다. SecureRandom 은 스레드 안전해서 필드 하나를 공유해도 된다.
    private final SecureRandom secureRandom = new SecureRandom();

    // 왜 ttl 을 인자로 받나: 세션의 수명은 방의 수명을 따른다. 그 값은 room 도메인이 알고 있다.
    public String issue(UUID participantId, String nickname, String roomCode, Duration ttl) {
        String token = generateToken();
        GuestSession session = new GuestSession(participantId, nickname, roomCode);
        guestSessionRedisRepository.save(token, session, ttl);
        return token;
    }

    // 왜 URL-safe Base64: 토큰은 HTTP 헤더와 WebSocket 연결 주소에 실린다. '+', '/', '=' 가 없어야 인코딩 문제가 없다.
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
