package com.muq.room.dto;

/**
 * "방에 들어왔다"의 응답. 방을 만든 호스트와 코드로 입장한 게스트가 같은 모양으로 받는다.
 * 왜 둘로 나눴나: room 은 방의 모든 참여자가 보는 정보이고, session 은 이 요청을 보낸 사람만 가져야 하는 정보다.
 */
public record RoomEntryResponse(RoomResponse room, SessionResponse session) {
}
