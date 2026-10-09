package com.muq.room.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muq.room.domain.MemberRole;
import com.muq.room.domain.PlayerProvider;
import com.muq.room.domain.RoomMode;
import com.muq.room.domain.RoomStatus;
import com.muq.room.dto.CreateRoomRequest;
import com.muq.room.dto.CreateRoomResponse;
import com.muq.room.service.RoomService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * RoomController 가 HTTP 계약(상태 코드, 헤더, 검증)을 지키는지 검증한다.
 * 서비스는 목으로 바꿔서 웹 계층만 올린다. 방이 실제로 저장되는지는 서비스·통합 테스트의 몫이다.
 */
@WebMvcTest(RoomController.class)
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    @Test
    @DisplayName("방을 만들면 201과 Location 헤더, 방 코드와 호스트 세션을 응답한다")
    void create() throws Exception {
        given(roomService.create(any(CreateRoomRequest.class))).willReturn(sampleResponse());

        postRoom("{\"nickname\": \"나노\", \"mode\": \"SPEAKER\", \"playerProvider\": \"YOUTUBE\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/rooms/K7M2QX"))
                .andExpect(jsonPath("$.room.code").value("K7M2QX"))
                .andExpect(jsonPath("$.room.status").value("ACTIVE"))
                .andExpect(jsonPath("$.session.token").value("token-abc"))
                .andExpect(jsonPath("$.session.role").value("HOST"));
    }

    @Test
    @DisplayName("닉네임이 비어 있으면 400이고 서비스를 호출하지 않는다")
    void blankNickname() throws Exception {
        postRoom("{\"nickname\": \" \", \"mode\": \"SPEAKER\", \"playerProvider\": \"YOUTUBE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("mode가 빠지면 400이다")
    void missingMode() throws Exception {
        postRoom("{\"nickname\": \"나노\", \"playerProvider\": \"YOUTUBE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("mode"));

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("지원하지 않는 playerProvider 값이면 400이다")
    void unknownProvider() throws Exception {
        postRoom("{\"nickname\": \"나노\", \"mode\": \"SPEAKER\", \"playerProvider\": \"MELON\"}")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roomService);
    }

    private ResultActions postRoom(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private CreateRoomResponse sampleResponse() {
        CreateRoomResponse.RoomInfo room = new CreateRoomResponse.RoomInfo(
                "K7M2QX", RoomMode.SPEAKER, PlayerProvider.YOUTUBE, RoomStatus.ACTIVE,
                Instant.parse("2026-10-10T09:00:00Z"));
        CreateRoomResponse.SessionInfo session = new CreateRoomResponse.SessionInfo(
                "token-abc", UUID.randomUUID(), "나노", MemberRole.HOST);
        return new CreateRoomResponse(room, session);
    }
}
