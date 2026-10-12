package com.muq.auth.domain;

import java.util.UUID;

/**
 * 로그인 없이 방에 들어온 참여자의 세션.
 * 토큰으로 이 값을 찾을 수 있으면 "누가, 어느 방에" 있는지 알 수 있다. 세션 하나는 방 하나에만 속한다.
 * 역할(HOST/GUEST)은 넣지 않는다. 방 안의 권한은 room 도메인이 판단할 일이다.
 */
public record GuestSession(UUID participantId, String nickname, String roomCode) {
}
