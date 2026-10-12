package com.muq.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.muq.auth.domain.GuestSession;
import com.muq.auth.repository.GuestSessionRedisRepository;
import com.muq.room.repository.RoomMemberRepository;
import com.muq.room.repository.RoomRepository;
import com.muq.support.IntegrationTestSupport;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * "호스트가 방을 만들고 → 게스트가 코드로 조회하고 → 입장한다"를 HTTP 로 끝에서 끝까지 검증한다.
 * 목이 없다. 실제 사용 순서 그대로 API 세 개가 서로 맞물리는지 확인한다.
 */
class RoomJoinApiTest extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private GuestSessionRedisRepository guestSessionRedisRepository;

    @AfterEach
    void cleanUp() {
        roomMemberRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("만든 방을 소문자 코드로 조회하면 200이고 세션 정보는 없다")
    void getRoom() {
        String code = createRoom();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/rooms/{code}", String.class, code.toLowerCase(Locale.ROOT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DocumentContext json = JsonPath.parse(response.getBody());
        assertThat(json.read("$.code", String.class)).isEqualTo(code);
        assertThat(json.read("$.status", String.class)).isEqualTo("ACTIVE");
        assertThat(response.getBody()).doesNotContain("token");
    }

    @Test
    @DisplayName("게스트로 입장하면 201이고, 받은 토큰은 그 방의 세션이며 멤버는 두 명이 된다")
    void joinAsGuest() {
        String code = createRoom();

        ResponseEntity<String> response = postGuest(code, "민수");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DocumentContext json = JsonPath.parse(response.getBody());
        assertThat(json.read("$.room.code", String.class)).isEqualTo(code);
        assertThat(json.read("$.session.role", String.class)).isEqualTo("GUEST");

        String token = json.read("$.session.token", String.class);
        Optional<GuestSession> session = guestSessionRedisRepository.findByToken(token);
        assertThat(session).isPresent();
        assertThat(session.get().roomCode()).isEqualTo(code);
        assertThat(session.get().nickname()).isEqualTo("민수");
        assertThat(roomMemberRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 방은 조회도 입장도 404 ROOM_NOT_FOUND다")
    void unknownRoom() {
        ResponseEntity<String> getResponse = restTemplate.getForEntity("/api/v1/rooms/ZZZZZZ", String.class);
        ResponseEntity<String> joinResponse = postGuest("ZZZZZZ", "민수");

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JsonPath.parse(getResponse.getBody()).read("$.code", String.class))
                .isEqualTo("ROOM_NOT_FOUND");
        assertThat(joinResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("닉네임 없이 입장하면 400이고 멤버는 호스트 한 명 그대로다")
    void joinWithBlankNickname() {
        String code = createRoom();

        ResponseEntity<String> response = postGuest(code, "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(roomMemberRepository.count()).isEqualTo(1);
    }

    private String createRoom() {
        Map<String, String> body = Map.of(
                "nickname", "나노",
                "mode", "SPEAKER",
                "playerProvider", "YOUTUBE");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/rooms", body, String.class);
        return JsonPath.parse(response.getBody()).read("$.room.code", String.class);
    }

    private ResponseEntity<String> postGuest(String code, String nickname) {
        return restTemplate.postForEntity(
                "/api/v1/rooms/{code}/guest", Map.of("nickname", nickname), String.class, code);
    }
}
