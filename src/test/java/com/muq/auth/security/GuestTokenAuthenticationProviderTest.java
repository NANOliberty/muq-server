package com.muq.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muq.auth.domain.GuestSession;
import com.muq.auth.repository.GuestSessionRedisRepository;
import com.muq.global.security.GuestTokenAuthentication;
import com.muq.global.security.ParticipantPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 토큰 → 세션 → 참여자로 바뀌는 인증 규칙을 검증한다.
 * Redis 에 저장·조회되는 것은 GuestSessionRedisRepositoryTest 가 이미 검증했으므로 여기서는 저장소를 목으로 둔다.
 */
class GuestTokenAuthenticationProviderTest {

    private final GuestSessionRedisRepository repository = mock(GuestSessionRedisRepository.class);
    private final GuestTokenAuthenticationProvider provider = new GuestTokenAuthenticationProvider(repository);

    @Test
    @DisplayName("Redis에 있는 토큰이면 그 세션의 참여자로 인증된다")
    void validToken() {
        UUID participantId = UUID.randomUUID();
        given(repository.findByToken("valid-token"))
                .willReturn(Optional.of(new GuestSession(participantId, "민수", "K7M2QX")));

        Authentication result = provider.authenticate(GuestTokenAuthentication.unauthenticated("valid-token"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isEqualTo(new ParticipantPrincipal(participantId, "민수", "K7M2QX"));
        assertThat(result.getName()).isEqualTo(participantId.toString());
        // 인증된 객체는 토큰을 들고 다니지 않는다.
        assertThat(result.getCredentials()).isNull();
    }

    @Test
    @DisplayName("Redis에 없는(만료된) 토큰이면 BadCredentialsException이다")
    void unknownToken() {
        given(repository.findByToken("expired-token")).willReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(GuestTokenAuthentication.unauthenticated("expired-token")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("게스트 토큰 인증만 맡고, 다른 종류의 인증은 맡지 않는다")
    void supports() {
        assertThat(provider.supports(GuestTokenAuthentication.class)).isTrue();
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }
}
