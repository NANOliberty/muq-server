package com.muq.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.muq.room.repository.RoomMemberRepository;
import com.muq.room.repository.RoomRepository;
import com.muq.support.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 인증을 HTTP 부터 Redis 까지 끝에서 끝으로 검증한다. 목이 없다.
 * 실제로 방을 만들고 입장해서 받은 진짜 토큰으로, 방에 속한 테스트 전용 API(test-whoami)를 부른다.
 * 왜 테스트 전용 API 인가: 토큰을 요구하는 실제 API(큐)는 아직 없다. 규칙이 /rooms/{code}/** 전체에 걸리므로 경로만 맞으면 된다.
 */
@Import(SecurityIntegrationTest.WhoAmIController.class)
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @AfterEach
    void cleanUp() {
        roomMemberRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("방을 만들고 받은 토큰으로 그 방의 API를 부르면 200이고, 컨트롤러는 그 참여자를 받는다")
    void hostToken() {
        DocumentContext room = createRoom();
        String code = room.read("$.room.code", String.class);

        ResponseEntity<String> response = whoAmI(code, room.read("$.session.token", String.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DocumentContext me = JsonPath.parse(response.getBody());
        assertThat(me.read("$.participantId", String.class))
                .isEqualTo(room.read("$.session.participantId", String.class));
        assertThat(me.read("$.nickname", String.class)).isEqualTo("나노");
        assertThat(me.read("$.roomCode", String.class)).isEqualTo(code);
    }

    @Test
    @DisplayName("게스트로 입장해 받은 토큰도 그 방의 API를 부를 수 있고, 주소의 코드는 소문자여도 된다")
    void guestToken() {
        String code = createRoom().read("$.room.code", String.class);
        DocumentContext guest = JsonPath.parse(restTemplate.postForEntity(
                "/api/v1/rooms/{code}/guest", Map.of("nickname", "민수"), String.class, code).getBody());

        ResponseEntity<String> response = whoAmI(code.toLowerCase(), guest.read("$.session.token", String.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.parse(response.getBody()).read("$.nickname", String.class)).isEqualTo("민수");
    }

    @Test
    @DisplayName("다른 방의 토큰이면 403 ROOM_ACCESS_DENIED다")
    void otherRoomToken() {
        String codeA = createRoom().read("$.room.code", String.class);
        String tokenOfB = createRoom().read("$.session.token", String.class);

        ResponseEntity<String> response = whoAmI(codeA, tokenOfB);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(JsonPath.parse(response.getBody()).read("$.code", String.class)).isEqualTo("ROOM_ACCESS_DENIED");
    }

    @Test
    @DisplayName("토큰이 없으면 401이고 WWW-Authenticate: Bearer를 붙인다")
    void noToken() {
        String code = createRoom().read("$.room.code", String.class);

        ResponseEntity<String> response = whoAmI(code, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(JsonPath.parse(response.getBody()).read("$.code", String.class)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Redis에 없는 토큰이면 401이고 invalid_token을 알린다")
    void unknownToken() {
        String code = createRoom().read("$.room.code", String.class);

        ResponseEntity<String> response = whoAmI(code, "this-token-was-never-issued");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer error=\"invalid_token\"");
    }

    @Test
    @DisplayName("공개 API는 가짜 토큰을 들고 와도 헤더를 보지 않고 그대로 동작한다")
    void publicApiIgnoresToken() {
        HttpHeaders headers = bearer("this-token-was-never-issued");
        Map<String, String> body = Map.of("nickname", "나노", "mode", "SPEAKER", "playerProvider", "YOUTUBE");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/rooms", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private DocumentContext createRoom() {
        Map<String, String> body = Map.of("nickname", "나노", "mode", "SPEAKER", "playerProvider", "YOUTUBE");
        return JsonPath.parse(restTemplate.postForEntity("/api/v1/rooms", body, String.class).getBody());
    }

    private ResponseEntity<String> whoAmI(String code, String token) {
        HttpHeaders headers = token == null ? new HttpHeaders() : bearer(token);
        return restTemplate.exchange(
                "/api/v1/rooms/{code}/test-whoami", HttpMethod.GET, new HttpEntity<>(headers), String.class, code);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    // 테스트에서만 존재하는 "방에 속한 API". 인증을 통과하면 컨트롤러가 받은 참여자를 그대로 돌려준다.
    @RestController
    static class WhoAmIController {

        @GetMapping("/api/v1/rooms/{code}/test-whoami")
        ParticipantPrincipal whoAmI(@AuthenticationPrincipal ParticipantPrincipal me) {
            return me;
        }
    }
}
