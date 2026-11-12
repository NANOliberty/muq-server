package com.muq.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Authorization 헤더에서 Bearer 토큰을 꺼내 "아직 검증하지 않은" GuestTokenAuthentication 으로 바꾼다.
 * 여기서는 형식만 본다. 토큰이 실제로 유효한지(Redis 에 있는지)는 Provider 가 판단한다.
 */
public class BearerTokenAuthenticationConverter implements AuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    // 왜 이 패턴: RFC 6750 의 b64token 문법이다. 우리 토큰(URL-safe Base64)보다 넓지만,
    //   M2 의 JWT(점 포함)도 같은 헤더로 들어오므로 표준 문법을 그대로 쓴다. 공백·한글 같은 값은 여기서 걸러진다.
    private static final Pattern B64_TOKEN = Pattern.compile("^[A-Za-z0-9\\-._~+/]+=*$");

    // 왜 null 을 돌려주나: null 은 "인증을 시도하지 않음"이다. 토큰 없는 요청은 익명으로 다음 필터로 넘어가고,
    //   공개 경로면 통과, 보호된 경로면 인가 단계에서 401 이 된다. 다른 방식(Basic 등)의 헤더도 같은 취급이다.
    @Override
    public GuestTokenAuthentication convert(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).strip();
        // 왜 예외인가: Bearer 라고 해 놓고 토큰이 비었거나 형식이 틀렸다. 시도는 했지만 실패한 것이므로 401 이다.
        if (!B64_TOKEN.matcher(token).matches()) {
            throw new BadCredentialsException("Bearer 토큰의 형식이 올바르지 않다");
        }
        return GuestTokenAuthentication.unauthenticated(token);
    }
}
