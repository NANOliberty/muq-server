package com.muq.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게스트 입장 요청. 방 코드는 주소에 있으므로 본문에는 닉네임만 온다.
 * 닉네임 규칙은 방 생성(CreateRoomRequest)과 같다. room_member.nickname 컬럼 길이(20)와 맞춘 값이다.
 */
public record JoinRoomRequest(@NotBlank @Size(max = 20) String nickname) {
}
