package com.muq.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * 주소의 방 코드와 세션의 방 코드를 비교하는 규칙을 검증한다.
 * 필터 체인 없이, 경로 변수를 직접 넣은 RequestAuthorizationContext 로 판단 결과만 본다.
 */
class RoomMemberAuthorizationManagerTest {

    private final RoomMemberAuthorizationManager manager = new RoomMemberAuthorizationManager();

    @Test
    @DisplayName("세션의 방과 주소의 방이 같으면 허용한다")
    void sameRoom() {
        assertThat(isGranted(participantOf("K7M2QX"), "K7M2QX")).isTrue();
    }

    @Test
    @DisplayName("주소의 방 코드가 소문자여도 같은 방이면 허용한다")
    void sameRoomIgnoringCase() {
        assertThat(isGranted(participantOf("K7M2QX"), "k7m2qx")).isTrue();
    }

    @Test
    @DisplayName("다른 방의 세션이면 거부한다")
    void otherRoom() {
        assertThat(isGranted(participantOf("K7M2QX"), "ZZZZZZ")).isFalse();
    }

    @Test
    @DisplayName("익명 사용자는 거부한다 (isAuthenticated가 true여도 참여자가 아니다)")
    void anonymous() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(anonymous.isAuthenticated()).isTrue();
        assertThat(isGranted(anonymous, "K7M2QX")).isFalse();
    }

    @Test
    @DisplayName("경로에 방 코드 변수가 없으면 거부한다")
    void missingRoomCode() {
        RequestAuthorizationContext context = new RequestAuthorizationContext(new MockHttpServletRequest(), Map.of());

        assertThat(manager.check(() -> participantOf("K7M2QX"), context).isGranted()).isFalse();
    }

    private boolean isGranted(Authentication authentication, String roomCodeInPath) {
        RequestAuthorizationContext context = new RequestAuthorizationContext(
                new MockHttpServletRequest(), Map.of(RoomMemberAuthorizationManager.ROOM_CODE_VARIABLE, roomCodeInPath));
        return manager.check(() -> authentication, context).isGranted();
    }

    private Authentication participantOf(String roomCode) {
        return GuestTokenAuthentication.authenticated(new ParticipantPrincipal(UUID.randomUUID(), "민수", roomCode));
    }
}
