package com.muq.global.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * 게스트 세션 토큰 인증의 "전"과 "후"를 나타내는 Authentication.
 * 전(unauthenticated): 헤더에서 꺼낸 토큰만 있다. 아직 누구인지 모른다.
 * 후(authenticated): Redis 에서 찾은 ParticipantPrincipal 이 있다. 토큰은 더 이상 들고 다니지 않는다.
 */
public class GuestTokenAuthentication extends AbstractAuthenticationToken {

    private String token;
    private final ParticipantPrincipal principal;

    private GuestTokenAuthentication(String token, ParticipantPrincipal principal, boolean authenticated) {
        // 왜 권한 목록이 비었나: 지금 권한 규칙은 "그 방의 참여자인가" 하나뿐이고, 그건 principal.roomCode 로 판단한다.
        super(null);
        this.token = token;
        this.principal = principal;
        super.setAuthenticated(authenticated);
    }

    public static GuestTokenAuthentication unauthenticated(String token) {
        return new GuestTokenAuthentication(token, null, false);
    }

    public static GuestTokenAuthentication authenticated(ParticipantPrincipal principal) {
        return new GuestTokenAuthentication(null, principal, true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public ParticipantPrincipal getPrincipal() {
        return principal;
    }

    // 왜 막나: 밖에서 setAuthenticated(true) 로 "인증됨"을 만들 수 있으면 Provider 를 건너뛸 수 있다.
    //   인증된 객체는 authenticated() 로만 만든다. 스프링의 UsernamePasswordAuthenticationToken 과 같은 규칙이다.
    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("인증된 객체는 GuestTokenAuthentication.authenticated() 로만 만든다");
        }
        super.setAuthenticated(false);
    }

    // 왜: 인증이 끝나면 ProviderManager 가 이 메서드를 불러 비밀 값을 지운다. 토큰이 SecurityContext 에 남지 않는다.
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.token = null;
    }
}
