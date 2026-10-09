package com.muq.room.service;

import com.muq.room.dto.CreateRoomRequest;
import com.muq.room.dto.CreateRoomResponse;
import org.springframework.stereotype.Service;

/**
 * 방의 생성과 수명을 다룬다.
 * 지금은 컨트롤러가 의존할 메서드 시그니처만 있다. 본문은 도메인·저장소 레이어를 만든 뒤 채운다.
 */
@Service
public class RoomService {

    public CreateRoomResponse create(CreateRoomRequest request) {
        throw new UnsupportedOperationException("방 생성 본문은 아직 구현 전이다");
    }
}
