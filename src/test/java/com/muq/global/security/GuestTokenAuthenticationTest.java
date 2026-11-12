package com.muq.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 인증 객체가 "인증됨" 상태를 함부로 만들 수 없고, 비밀 값(토큰)을 남기지 않는지 검증한다.
 */
class GuestTokenAuthenticationTest {

    @Test
    @DisplayName("밖에서 setAuthenticated(true)로 인증된 상태를 만들 수 없다")
    void cannotForceAuthenticated() {
        GuestTokenAuthentication authentication = GuestTokenAuthentication.unauthenticated("token");

        assertThatThrownBy(() -> authentication.setAuthenticated(true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(authentication.isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("eraseCredentials를 부르면 토큰이 지워진다")
    void eraseCredentials() {
        GuestTokenAuthentication authentication = GuestTokenAuthentication.unauthenticated("token");

        authentication.eraseCredentials();

        assertThat(authentication.getCredentials()).isNull();
    }

    @Test
    @DisplayName("toString에 토큰이 찍히지 않는다")
    void toStringHidesToken() {
        GuestTokenAuthentication authentication = GuestTokenAuthentication.unauthenticated("secret-token-value");

        assertThat(authentication.toString()).doesNotContain("secret-token-value");
    }
}
