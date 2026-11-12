package com.muq.room.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muq.auth.domain.GuestSession;
import com.muq.auth.repository.GuestSessionRedisRepository;
import com.muq.auth.security.GuestTokenAuthenticationProvider;
import com.muq.global.error.BusinessException;
import com.muq.global.error.ErrorCode;
import com.muq.global.security.ProblemDetailSecurityErrorHandler;
import com.muq.global.security.SecurityConfig;
import com.muq.room.domain.MemberRole;
import com.muq.room.domain.PlayerProvider;
import com.muq.room.domain.RoomMode;
import com.muq.room.domain.RoomStatus;
import com.muq.room.dto.CreateRoomRequest;
import com.muq.room.dto.JoinRoomRequest;
import com.muq.room.dto.RoomEntryResponse;
import com.muq.room.dto.RoomResponse;
import com.muq.room.dto.SessionResponse;
import com.muq.room.service.RoomService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
// 왜: @WebMvcTest 는 우리 SecurityConfig 를 읽지 않고 Boot 기본 보안(전부 잠금, CSRF 켜짐)으로 뜬다.
//   방 API 는 "토큰 없이 부를 수 있다"는 것도 계약이므로, 실제 규칙을 올려 함께 검증한다.
@Import({SecurityConfig.class, ProblemDetailSecurityErrorHandler.class, GuestTokenAuthenticationProvider.class})
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    // 왜: 인증 Provider 가 세션을 찾는 저장소다. 웹 계층 테스트라 Redis 대신 목으로 둔다.
    @MockitoBean
    private GuestSessionRedisRepository guestSessionRedisRepository;

    @Test
    @DisplayName("방을 만들면 201과 Location 헤더, 방 코드와 호스트 세션을 응답한다")
    void create() throws Exception {
        given(roomService.create(any(CreateRoomRequest.class))).willReturn(sampleEntry(MemberRole.HOST));

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

    @Test
    @DisplayName("방을 조회하면 200과 방의 기본 정보를 응답한다")
    void getRoom() throws Exception {
        given(roomService.get("K7M2QX")).willReturn(sampleRoom());

        mockMvc.perform(get("/api/v1/rooms/{code}", "K7M2QX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("K7M2QX"))
                .andExpect(jsonPath("$.mode").value("SPEAKER"))
                .andExpect(jsonPath("$.session").doesNotExist());
    }

    @Test
    @DisplayName("없는 방을 조회하면 404 ROOM_NOT_FOUND다")
    void getUnknownRoom() throws Exception {
        given(roomService.get("ZZZZZZ")).willThrow(new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/rooms/{code}", "ZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("끝난 방을 조회하면 410 ROOM_CLOSED다")
    void getClosedRoom() throws Exception {
        given(roomService.get("K7M2QX")).willThrow(new BusinessException(ErrorCode.ROOM_CLOSED));

        mockMvc.perform(get("/api/v1/rooms/{code}", "K7M2QX"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ROOM_CLOSED"));
    }

    @Test
    @DisplayName("게스트로 입장하면 201과 방 정보, GUEST 세션을 응답한다")
    void joinAsGuest() throws Exception {
        given(roomService.joinAsGuest(eq("K7M2QX"), any(JoinRoomRequest.class)))
                .willReturn(sampleEntry(MemberRole.GUEST));

        postGuest("K7M2QX", "{\"nickname\": \"민수\"}")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.room.code").value("K7M2QX"))
                .andExpect(jsonPath("$.session.token").value("token-abc"))
                .andExpect(jsonPath("$.session.role").value("GUEST"));
    }

    @Test
    @DisplayName("입장할 때 닉네임이 비어 있으면 400이고 서비스를 호출하지 않는다")
    void joinWithBlankNickname() throws Exception {
        postGuest("K7M2QX", "{\"nickname\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("방의 공개되지 않은 경로에 토큰 없이 오면 401 Problem Details다")
    void protectedPathWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{code}/queue", "K7M2QX"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("보호된 경로에 유효한 토큰으로 오면 인증을 통과한다 (아직 그 경로의 API가 없어 404)")
    void protectedPathWithValidToken() throws Exception {
        given(guestSessionRedisRepository.findByToken("valid-token"))
                .willReturn(Optional.of(new GuestSession(UUID.randomUUID(), "민수", "K7M2QX")));

        mockMvc.perform(get("/api/v1/rooms/{code}/queue", "K7M2QX")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("보호된 경로에 Redis에 없는 토큰으로 오면 401이고 invalid_token을 알린다")
    void protectedPathWithUnknownToken() throws Exception {
        given(guestSessionRedisRepository.findByToken("expired-token")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/rooms/{code}/queue", "K7M2QX")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("공개 경로는 만료된 토큰을 들고 와도 헤더를 보지 않고 그대로 처리한다")
    void publicPathIgnoresStaleToken() throws Exception {
        given(roomService.create(any(CreateRoomRequest.class))).willReturn(sampleEntry(MemberRole.HOST));

        mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer expired-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"나노\", \"mode\": \"SPEAKER\", \"playerProvider\": \"YOUTUBE\"}"))
                .andExpect(status().isCreated());

        verifyNoInteractions(guestSessionRedisRepository);
    }

    private ResultActions postRoom(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions postGuest(String code, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{code}/guest", code)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private RoomResponse sampleRoom() {
        return new RoomResponse(
                "K7M2QX", RoomMode.SPEAKER, PlayerProvider.YOUTUBE, RoomStatus.ACTIVE,
                Instant.parse("2026-10-10T09:00:00Z"));
    }

    private RoomEntryResponse sampleEntry(MemberRole role) {
        SessionResponse session = new SessionResponse("token-abc", UUID.randomUUID(), "나노", role);
        return new RoomEntryResponse(sampleRoom(), session);
    }
}
