package com.muq.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muq.global.error.ErrorCode;
import com.muq.global.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 보안 필터에서 난 401·403 을 다른 API 와 같은 Problem Details 로 쓴다.
 * 이 에러들은 컨트롤러에 닿기 전에 필터에서 나므로 GlobalExceptionHandler(@RestControllerAdvice)가 보지 못한다.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // 왜 스프링이 만든 ObjectMapper 를 주입받나: ProblemDetail 의 추가 속성(code)을 최상위 필드로 펼치는 설정(mixin)이
    //   들어 있다. new ObjectMapper() 로 쓰면 code 가 "properties" 안에 들어가 다른 API 와 모양이 달라진다.
    private final ObjectMapper objectMapper;

    // 401. 토큰이 없거나, 형식이 틀렸거나, Redis 에 없는(만료된) 토큰일 때.
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, bearerChallenge(ex));
        write(request, response, ErrorCode.UNAUTHORIZED);
    }

    // 403. 인증은 됐지만 이 경로에 대한 권한이 없을 때. 지금 있는 권한 규칙은 "그 방의 참여자인가" 하나뿐이다.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, ErrorCode.ROOM_ACCESS_DENIED);
    }

    // 왜 WWW-Authenticate: 401 에는 "어떤 방식으로 인증하라"는 안내를 붙이는 것이 HTTP 규칙이다 (RFC 9110, RFC 6750).
    //   토큰을 보냈는데 틀린 경우에만 error="invalid_token" 을 붙인다. 토큰을 안 보낸 요청에는 오류 정보를 주지 않는다.
    private String bearerChallenge(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException) {
            return "Bearer error=\"invalid_token\"";
        }
        return "Bearer";
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        ProblemDetail problem = ProblemDetails.of(errorCode);
        // 왜 직접 넣나: 컨트롤러 응답은 스프링이 instance 를 채워 주지만, 필터에서 쓰는 응답은 채워 주지 않는다.
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
