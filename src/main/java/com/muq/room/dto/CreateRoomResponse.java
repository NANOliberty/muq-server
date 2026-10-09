package com.muq.room.dto;

import com.muq.room.domain.MemberRole;
import com.muq.room.domain.PlayerProvider;
import com.muq.room.domain.RoomMode;
import com.muq.room.domain.RoomStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * 방 생성 응답. 만들어진 방과, 만든 사람(호스트)의 세션을 함께 돌려준다.
 * 왜 둘로 나눴나: room 은 방의 모든 참여자가 보는 정보이고, session 은 이 요청을 보낸 사람만 가져야 하는 정보다.
 */
public record CreateRoomResponse(RoomInfo room, SessionInfo session) {

    public record RoomInfo(
            String code,
            RoomMode mode,
            PlayerProvider playerProvider,
            RoomStatus status,
            Instant expiresAt) {
    }

    // 왜 token 을 본문에 담나: 이후 요청에서 본인임을 증명할 유일한 수단이다. 이 응답에서 한 번만 내려 준다.
    public record SessionInfo(
            String token,
            UUID participantId,
            String nickname,
            MemberRole role) {
    }
}
