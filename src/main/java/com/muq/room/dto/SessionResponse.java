package com.muq.room.dto;

import com.muq.room.domain.MemberRole;
import java.util.UUID;

/**
 * 방에 들어온 사람 본인에게만 주는 세션 정보.
 * token 은 이후 요청에서 본인임을 증명할 유일한 수단이고, 방 생성·입장 응답에서 한 번만 내려 준다.
 */
public record SessionResponse(
        String token,
        UUID participantId,
        String nickname,
        MemberRole role) {
}
