package com.muq.auth.security;

import com.muq.auth.domain.GuestSession;
import com.muq.auth.repository.GuestSessionRedisRepository;
import com.muq.global.security.GuestTokenAuthentication;
import com.muq.global.security.ParticipantPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 게스트 세션 토큰이 진짜인지 확인한다. 토큰으로 Redis 의 guest:{token} 을 찾아, 있으면 그 참여자로 인증한다.
 * 왜 auth 패키지에 있나: 세션이 어디에 어떻게 저장되는지는 auth 도메인만 안다. 보안 설정(global)은 이 클래스를 모르고
 *   AuthenticationProvider 인터페이스로만 받는다.
 */
@Component
@RequiredArgsConstructor
public class GuestTokenAuthenticationProvider implements AuthenticationProvider {

    private final GuestSessionRedisRepository guestSessionRedisRepository;

    // 왜 매 요청 Redis 를 보나: 캐시를 두면 만료·강퇴가 즉시 반영되지 않는다. HGETALL 한 번이라 비용이 작다.
    @Override
    public Authentication authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        GuestSession session = guestSessionRedisRepository.findByToken(token)
                .orElseThrow(() -> new BadCredentialsException("유효하지 않거나 만료된 세션 토큰"));
        ParticipantPrincipal principal = new ParticipantPrincipal(
                session.participantId(), session.nickname(), session.roomCode());
        return GuestTokenAuthentication.authenticated(principal);
    }

    // 왜: ProviderManager 는 여러 Provider 중 이 메서드가 true 인 것에게만 맡긴다. M2 의 JWT 는 다른 Provider 가 받는다.
    @Override
    public boolean supports(Class<?> authentication) {
        return GuestTokenAuthentication.class.isAssignableFrom(authentication);
    }
}
