package com.muq.room.dto;

import com.muq.room.domain.PlayerProvider;
import com.muq.room.domain.RoomMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 방 생성 요청.
 * 로그인이 없으므로 호스트도 닉네임만으로 방을 만든다. 형식 검증은 여기서 끝내고 서비스는 규칙만 다룬다.
 */
public record CreateRoomRequest(
        @NotBlank @Size(max = 20) String nickname,
        @NotNull RoomMode mode,
        @NotNull PlayerProvider playerProvider) {
}
