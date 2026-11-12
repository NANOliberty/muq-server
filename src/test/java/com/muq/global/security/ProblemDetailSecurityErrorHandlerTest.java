package com.muq.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * 보안 필터의 401·403 이 다른 API 와 같은 Problem Details 로 나가는지 검증한다.
 * 스프링 컨텍스트 없이 가짜 요청·응답 객체로 핸들러만 직접 부른다.
 */
class ProblemDetailSecurityErrorHandlerTest {

    // 왜 Jackson2ObjectMapperBuilder: 스프링 부트가 만드는 ObjectMapper 와 같은 설정(ProblemDetail mixin 포함)을 쓴다.
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ProblemDetailSecurityErrorHandler handler = new ProblemDetailSecurityErrorHandler(objectMapper);

    @Test
    @DisplayName("토큰 없이 오면 401 UNAUTHORIZED이고 WWW-Authenticate는 Bearer만 담는다")
    void missingToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request(), response, new InsufficientAuthenticationException("토큰 없음"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith("application/problem+json");
        DocumentContext json = JsonPath.parse(response.getContentAsString());
        assertThat(json.read("$.code", String.class)).isEqualTo("UNAUTHORIZED");
        assertThat(json.read("$.status", Integer.class)).isEqualTo(401);
        assertThat(json.read("$.instance", String.class)).isEqualTo("/api/v1/rooms/K7M2QX/queue");
    }

    @Test
    @DisplayName("틀린 토큰이면 401이고 WWW-Authenticate에 invalid_token을 붙인다")
    void invalidToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request(), response, new BadCredentialsException("세션 없음"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
    }

    @Test
    @DisplayName("권한이 없으면 403 ROOM_ACCESS_DENIED이고 한글 문구가 깨지지 않는다")
    void accessDenied() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request(), response, new AccessDeniedException("다른 방"));

        assertThat(response.getStatus()).isEqualTo(403);
        DocumentContext json = JsonPath.parse(response.getContentAsString());
        assertThat(json.read("$.code", String.class)).isEqualTo("ROOM_ACCESS_DENIED");
        assertThat(json.read("$.detail", String.class)).isEqualTo("이 방에 접근할 권한이 없습니다.");
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/rooms/K7M2QX/queue");
    }
}
