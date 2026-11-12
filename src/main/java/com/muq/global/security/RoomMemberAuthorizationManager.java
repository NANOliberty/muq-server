package com.muq.global.security;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * "이 요청을 보낸 참여자가 주소에 적힌 그 방의 참여자인가"를 판단한다.
 * /api/v1/rooms/{code}/** 규칙에 걸려 있어서, 그 아래에 생기는 큐·투표·재생 API 는 컨트롤러에서 따로 확인하지 않아도 보호된다.
 */
public class RoomMemberAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    static final String ROOM_CODE_VARIABLE = "code";

    // 왜 check 를 구현하나: 이 버전(Spring Security 6.5)에서 구현을 요구하는 메서드다. authorize() 는 이것을 부른다.
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        // 왜 principal 의 타입을 보나: 익명 사용자도 isAuthenticated() 는 true 다. 세션 토큰으로 인증된 사람만 참여자다.
        //   익명이면 거부 → ExceptionTranslationFilter 가 "익명이니 인증부터"로 보고 401 을 낸다.
        if (!(authentication.get().getPrincipal() instanceof ParticipantPrincipal participant)) {
            return new AuthorizationDecision(false);
        }
        String requestedRoomCode = context.getVariables().get(ROOM_CODE_VARIABLE);
        // 왜 대소문자 무시: 서비스와 같은 규칙이다. 주소의 코드는 사람이 입력할 수 있고, 저장된 코드는 항상 대문자다.
        boolean sameRoom = requestedRoomCode != null && participant.roomCode().equalsIgnoreCase(requestedRoomCode);
        return new AuthorizationDecision(sameRoom);
    }
}
