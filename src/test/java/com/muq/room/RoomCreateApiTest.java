package com.muq.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.muq.auth.domain.GuestSession;
import com.muq.auth.repository.GuestSessionRedisRepository;
import com.muq.room.repository.RoomMemberRepository;
import com.muq.room.repository.RoomRepository;
import com.muq.support.IntegrationTestSupport;
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
 * 방 생성을 HTTP 요청부터 PostgreSQL·Redis 까지 끝에서 끝으로 검증한다.
 * 목이 하나도 없다. 레이어별 테스트가 각자 통과해도 서로 맞물리지 않을 수 있어서, 실제 요청 하나를 그대로 흘려 본다.
 */
class RoomCreateApiTest extends IntegrationTestSupport {

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
    @DisplayName("방 생성 요청은 201이고, 응답의 토큰으로 그 방의 세션을 찾을 수 있다")
    void create() {
        ResponseEntity<String> response = postRoom("나노");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DocumentContext json = JsonPath.parse(response.getBody());
        String code = json.read("$.room.code", String.class);
        assertThat(code).matches("[2-9A-HJKMNP-Z]{6}");
        assertThat(response.getHeaders().getLocation()).hasPath("/api/v1/rooms/" + code);
        assertThat(json.read("$.session.role", String.class)).isEqualTo("HOST");

        String token = json.read("$.session.token", String.class);
        Optional<GuestSession> session = guestSessionRedisRepository.findByToken(token);
        assertThat(session).isPresent();
        assertThat(session.get().roomCode()).isEqualTo(code);
        assertThat(session.get().nickname()).isEqualTo("나노");
        assertThat(roomRepository.count()).isEqualTo(1);
        assertThat(roomMemberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("닉네임이 비어 있으면 400이고 방은 만들어지지 않는다")
    void blankNickname() {
        ResponseEntity<String> response = postRoom("");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        DocumentContext json = JsonPath.parse(response.getBody());
        assertThat(json.read("$.code", String.class)).isEqualTo("INVALID_INPUT");
        assertThat(roomRepository.count()).isZero();
    }

    private ResponseEntity<String> postRoom(String nickname) {
        Map<String, String> body = Map.of(
                "nickname", nickname,
                "mode", "SPEAKER",
                "playerProvider", "YOUTUBE");
        return restTemplate.postForEntity("/api/v1/rooms", body, String.class);
    }
}
