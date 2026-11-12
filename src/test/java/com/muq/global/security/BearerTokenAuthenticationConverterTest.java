package com.muq.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Authorization 헤더를 어떻게 해석하는지 검증한다.
 * "인증을 시도하지 않음(null)"과 "시도했지만 형식이 틀림(예외)"을 구분하는 것이 핵심이다.
 */
class BearerTokenAuthenticationConverterTest {

    private final BearerTokenAuthenticationConverter converter = new BearerTokenAuthenticationConverter();

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증을 시도하지 않는다 (null)")
    void noHeader() {
        assertThat(converter.convert(new MockHttpServletRequest())).isNull();
    }

    @Test
    @DisplayName("Bearer가 아닌 방식(Basic)의 헤더도 인증을 시도하지 않는다 (null)")
    void otherScheme() {
        assertThat(converter.convert(requestWithAuthorization("Basic dXNlcjpwYXNz"))).isNull();
    }

    @Test
    @DisplayName("Bearer 토큰을 꺼내 아직 인증되지 않은 Authentication을 만든다")
    void bearerToken() {
        GuestTokenAuthentication result = converter.convert(requestWithAuthorization("Bearer abc_DEF-123"));

        assertThat(result.getCredentials()).isEqualTo("abc_DEF-123");
        assertThat(result.isAuthenticated()).isFalse();
        assertThat(result.getPrincipal()).isNull();
    }

    @Test
    @DisplayName("Bearer는 대소문자를 가리지 않는다")
    void schemeIsCaseInsensitive() {
        GuestTokenAuthentication result = converter.convert(requestWithAuthorization("bearer abc_DEF-123"));

        assertThat(result.getCredentials()).isEqualTo("abc_DEF-123");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"Bearer ", "Bearer    ", "Bearer abc def", "Bearer 토큰"})
    @DisplayName("Bearer 뒤가 비었거나 형식이 틀리면 BadCredentialsException이다")
    void malformedToken(String header) {
        assertThatThrownBy(() -> converter.convert(requestWithAuthorization(header)))
                .isInstanceOf(BadCredentialsException.class);
    }

    private MockHttpServletRequest requestWithAuthorization(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", value);
        return request;
    }
}
