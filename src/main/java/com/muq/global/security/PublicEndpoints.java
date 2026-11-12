package com.muq.global.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 토큰 없이 부를 수 있는 경로의 단 한 목록.
 * 인가 규칙(permitAll)과 인증 필터(이 경로에서는 Authorization 헤더를 보지 않음)가 같은 목록을 쓴다.
 * 두 곳에 따로 적으면 한쪽만 고쳐서 "열려 있는데 토큰 검사는 하는" 경로가 생긴다.
 */
public final class PublicEndpoints {

    private static final PathPatternRequestMatcher.Builder PATH = PathPatternRequestMatcher.withDefaults();

    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            // 토큰을 받기 전에 부르는 API
            PATH.matcher(HttpMethod.POST, "/api/v1/rooms"),
            PATH.matcher(HttpMethod.GET, "/api/v1/rooms/{code}"),
            PATH.matcher(HttpMethod.POST, "/api/v1/rooms/{code}/guest"),
            // 운영·문서
            PATH.matcher("/actuator/health"),
            PATH.matcher("/actuator/health/**"),
            PATH.matcher("/actuator/info"),
            PATH.matcher("/swagger-ui.html"),
            PATH.matcher("/swagger-ui/**"),
            PATH.matcher("/v3/api-docs/**"),
            // 왜 /error: 처리 중 예외가 서블릿 에러 페이지로 넘어가면 이 경로로 다시 들어온다.
            //   막아 두면 원래 에러(400·500) 대신 인증 에러가 응답으로 나가 원인이 가려진다.
            PATH.matcher("/error"));

    private PublicEndpoints() {
    }
}
