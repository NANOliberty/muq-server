package com.muq.room.controller;

import com.muq.room.dto.CreateRoomRequest;
import com.muq.room.dto.RoomEntryResponse;
import com.muq.room.service.RoomService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방 관련 HTTP 요청의 입구.
 * 요청 형식을 검증하고 서비스를 호출한 뒤 HTTP 응답(상태 코드·헤더)으로 바꾸는 일만 한다. 규칙은 서비스에 있다.
 */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    // 왜 201 + Location: 새 리소스가 생겼다는 것과 그 주소를 HTTP 표준대로 알려 준다.
    @PostMapping
    public ResponseEntity<RoomEntryResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        RoomEntryResponse response = roomService.create(request);
        URI location = URI.create("/api/v1/rooms/" + response.room().code());
        return ResponseEntity.created(location).body(response);
    }
}
