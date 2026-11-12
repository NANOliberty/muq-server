package com.muq.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 어떤 요청을 누구에게 허용할지 정하는 보안 규칙의 단 한 곳.
 * 지금은 "토큰 없이 부를 수 있는 공개 경로"와 "그 밖은 인증 필요", 그리고 401·403 의 응답 형식이 있다.
 * 세션 토큰 인증 필터와 방 단위 인가 규칙은 이후 단계에서 여기에 더한다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ProblemDetailSecurityErrorHandler securityErrorHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 왜 끄나: CSRF 는 브라우저가 쿠키를 자동으로 실어 보내서 생기는 공격이다.
                //   우리 토큰은 클라이언트가 Authorization 헤더에 직접 넣으므로 다른 사이트가 대신 보낼 수 없다.
                .csrf(AbstractHttpConfigurer::disable)
                // 왜 끄나: 로그인 폼·Basic 인증·로그아웃 페이지는 쓰지 않는다. 켜 두면 401 대신 로그인 페이지로 보낸다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 왜 STATELESS: 세션 상태는 Redis(guest:{token})에 있다. 서버 메모리(HttpSession)에 두면 서버가 여러 대일 때 어긋난다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 왜: 기본값은 본문 없는 403 이다. 다른 API 와 같은 Problem Details 로 401·403 을 구분해 내보낸다.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(this::authorizeRequests);
        return http.build();
    }

    // 규칙은 위에서부터 처음 맞는 것 하나만 적용된다. 넓은 규칙(anyRequest)은 반드시 맨 아래에 둔다.
    private void authorizeRequests(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // 토큰을 받기 전에 부르는 API
                .requestMatchers(HttpMethod.POST, "/api/v1/rooms").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/rooms/{code}").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/rooms/{code}/guest").permitAll()
                // 운영·문서
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 왜 /error: 처리 중 예외가 서블릿 에러 페이지로 넘어가면 이 경로로 다시 들어온다.
                //   막아 두면 원래 에러(400·500) 대신 인증 에러가 응답으로 나가 원인이 가려진다.
                .requestMatchers("/error").permitAll()
                // 왜 기본이 "인증 필요"인가: 새 API 를 만들고 규칙을 빠뜨려도 열리지 않고 닫힌 채로 남게 한다.
                .anyRequest().authenticated();
    }
}
