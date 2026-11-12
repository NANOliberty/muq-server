package com.muq.global.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 어떤 요청을 누구에게 허용할지 정하는 보안 규칙의 단 한 곳.
 * 공개 경로(PublicEndpoints)는 토큰을 보지 않고 통과시키고, 그 밖의 요청은 세션 토큰으로 인증한다.
 * 방에 속한 API(/api/v1/rooms/{code}/**)는 그 방의 세션만 부를 수 있다. 401·403 은 다른 API 와 같은 Problem Details 로 내보낸다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    // 방 하나에 속한 모든 API. 공개 경로(방 조회·입장)는 위 규칙에서 먼저 걸러진다.
    private static final RequestMatcher ROOM_SCOPED =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/rooms/{code}/**");

    private final ProblemDetailSecurityErrorHandler securityErrorHandler;

    // 왜 인터페이스 목록으로 받나: 보안 설정은 토큰이 어디에 저장되는지(auth 도메인) 몰라도 된다.
    //   M2 에서 JWT Provider 빈이 생기면 코드 변경 없이 이 목록에 들어온다.
    private final List<AuthenticationProvider> authenticationProviders;

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
                // 왜 익명 필터 앞: 토큰이 있으면 먼저 인증해 두어야, 익명 필터가 "익명 사용자"로 덮어쓰지 않는다.
                .addFilterBefore(guestTokenAuthenticationFilter(), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(this::authorizeRequests);
        return http.build();
    }

    // 규칙은 위에서부터 처음 맞는 것 하나만 적용된다. 넓은 규칙(anyRequest)은 반드시 맨 아래에 둔다.
    private void authorizeRequests(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers(PublicEndpoints.MATCHER).permitAll()
                // 왜 경로 규칙으로 거나: 컨트롤러마다 "같은 방인가"를 확인하면 하나만 빠뜨려도 다른 방을 조작할 수 있다.
                //   {code} 는 RoomMemberAuthorizationManager 가 경로 변수로 꺼내 세션의 방과 비교한다.
                .requestMatchers(ROOM_SCOPED).access(new RoomMemberAuthorizationManager())
                // 왜 기본이 "인증 필요"인가: 새 API 를 만들고 규칙을 빠뜨려도 열리지 않고 닫힌 채로 남게 한다.
                .anyRequest().authenticated();
    }

    // 왜 @Bean 이 아닌가: Filter 를 빈으로 등록하면 스프링 부트가 서블릿 필터로도 한 번 더 등록해 요청마다 두 번 돈다.
    //   보안 필터 체인 안에서만 돌도록 여기서 직접 만든다.
    private AuthenticationFilter guestTokenAuthenticationFilter() {
        AuthenticationFilter filter = new AuthenticationFilter(
                new ProviderManager(authenticationProviders), new BearerTokenAuthenticationConverter());
        // 왜: 공개 경로에서는 Authorization 헤더를 아예 보지 않는다. 지난 방의 만료된 토큰을 들고
        //   새 방을 만들거나 다른 방에 입장해도 401 로 막히지 않는다.
        filter.setRequestMatcher(new NegatedRequestMatcher(PublicEndpoints.MATCHER));
        // 왜 아무것도 안 하나: 기본 성공 처리기는 로그인 폼용이라 "/" 로 리다이렉트한다.
        //   비워 두면 인증 정보만 SecurityContext 에 남기고 원래 요청을 그대로 이어서 처리한다.
        filter.setSuccessHandler((request, response, authentication) -> {
        });
        // 왜: 토큰이 틀리면 진입점(401 + WWW-Authenticate)으로 보낸다. 다른 401 과 같은 응답이 된다.
        filter.setFailureHandler(new AuthenticationEntryPointFailureHandler(securityErrorHandler));
        return filter;
    }
}
