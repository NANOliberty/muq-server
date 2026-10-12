package com.muq.room.dto;

import com.muq.room.domain.PlayerProvider;
import com.muq.room.domain.Room;
import com.muq.room.domain.RoomMode;
import com.muq.room.domain.RoomStatus;
import java.time.Instant;

/**
 * 방의 기본 정보. 코드를 아는 사람이면 누구나 볼 수 있는 값만 담는다.
 * 호스트 ID, 멤버 목록, 큐는 넣지 않는다. 입장하지 않은 사람도 이 응답을 받기 때문이다.
 */
public record RoomResponse(
        String code,
        RoomMode mode,
        PlayerProvider playerProvider,
        RoomStatus status,
        Instant expiresAt) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getCode(), room.getMode(), room.getPlayerProvider(), room.getStatus(), room.getExpiresAt());
    }
}
