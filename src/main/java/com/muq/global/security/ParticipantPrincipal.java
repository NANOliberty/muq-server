package com.muq.global.security;

import java.security.Principal;
import java.util.UUID;

/**
 * 인증된 요청을 보낸 사람. "누가(participantId, nickname), 어느 방에서(roomCode)"를 담는다.
 * 컨트롤러는 @AuthenticationPrincipal 로 이 값을 받는다. 로그인 여부와 무관하게 방 안의 신원은 이것 하나다 (ADR 0004).
 * 왜 global 에 두나: room·track 등 모든 도메인 컨트롤러가 쓴다. auth 에 두면 도메인끼리 컨트롤러에서 서로 참조하게 된다.
 */
public record ParticipantPrincipal(UUID participantId, String nickname, String roomCode) implements Principal {

    // 왜 participantId: 로그와 Spring Security 내부(getName)에 찍히는 값이다. 닉네임은 겹칠 수 있어 식별자로 쓰지 않는다.
    @Override
    public String getName() {
        return participantId.toString();
    }
}
