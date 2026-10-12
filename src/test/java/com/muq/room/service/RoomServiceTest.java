package com.muq.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muq.auth.domain.GuestSession;
import com.muq.auth.repository.GuestSessionRedisRepository;
import com.muq.global.error.BusinessException;
import com.muq.global.error.ErrorCode;
import com.muq.room.domain.MemberRole;
import com.muq.room.domain.PlayerProvider;
import com.muq.room.domain.Room;
import com.muq.room.domain.RoomMember;
import com.muq.room.domain.RoomMode;
import com.muq.room.dto.CreateRoomRequest;
import com.muq.room.dto.CreateRoomResponse;
import com.muq.room.repository.RoomMemberRepository;
import com.muq.room.repository.RoomRepository;
import com.muq.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 방 생성의 규칙을 실제 PostgreSQL·Redis 위에서 검증한다.
 * 코드 생성기와 시계만 목으로 바꾼다. 코드 충돌을 원하는 대로 일으키고 만료 시각을 고정하기 위해서다.
 * 유니크 제약, 트랜잭션 롤백, 세션 저장은 진짜로 일어나야 의미가 있으므로 바꾸지 않는다.
 */
class RoomServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");
    private static final Duration TTL = Duration.ofHours(6);

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private GuestSessionRedisRepository guestSessionRedisRepository;

    @MockitoBean
    private RoomCodeGenerator roomCodeGenerator;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void fixClock() {
        given(clock.instant()).willReturn(NOW);
    }

    @AfterEach
    void cleanUp() {
        roomMemberRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("방을 만들면 방, 호스트 멤버, 호스트 세션이 모두 저장된다")
    void create() {
        given(roomCodeGenerator.generate()).willReturn("AAAAAA");

        CreateRoomResponse response = roomService.create(request("나노"));

        Room room = roomRepository.findAll().getFirst();
        assertThat(room.getCode()).isEqualTo("AAAAAA");
        assertThat(room.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        RoomMember host = roomMemberRepository.findAll().getFirst();
        assertThat(host.getRole()).isEqualTo(MemberRole.HOST);
        assertThat(host.getParticipantId()).isEqualTo(room.getHostId());
        assertThat(guestSessionRedisRepository.findByToken(response.session().token()))
                .contains(new GuestSession(room.getHostId(), "나노", "AAAAAA"));
    }

    @Test
    @DisplayName("코드가 기존 방과 겹치면 새 코드로 다시 시도해서 성공한다")
    void retryOnCodeConflict() {
        saveExistingRoom("AAAAAA");
        given(roomCodeGenerator.generate()).willReturn("AAAAAA", "AAAAAA", "BBBBBB");

        CreateRoomResponse response = roomService.create(request("나노"));

        assertThat(response.room().code()).isEqualTo("BBBBBB");
        verify(roomCodeGenerator, times(3)).generate();
        assertThat(roomRepository.count()).isEqualTo(2);
        // 실패한 두 번의 시도는 롤백되어 아무것도 남기지 않았다.
        assertThat(roomMemberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다섯 번 모두 겹치면 ROOM_CODE_EXHAUSTED로 실패한다")
    void exhausted() {
        saveExistingRoom("AAAAAA");
        given(roomCodeGenerator.generate()).willReturn("AAAAAA");

        assertThatThrownBy(() -> roomService.create(request("나노")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_CODE_EXHAUSTED);

        verify(roomCodeGenerator, times(5)).generate();
        assertThat(roomMemberRepository.count()).isZero();
    }

    @Test
    @DisplayName("코드 충돌이 아닌 제약 위반은 재시도하지 않고, 방도 남기지 않는다")
    void otherViolationIsNotRetried() {
        given(roomCodeGenerator.generate()).willReturn("AAAAAA");
        // 컨트롤러 검증(20자)을 거치지 않고 서비스를 직접 호출해 room_member.nickname 길이 제약을 넘긴다.
        String tooLongNickname = "가".repeat(21);

        assertThatThrownBy(() -> roomService.create(request(tooLongNickname)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(roomCodeGenerator, times(1)).generate();
        // 방 INSERT 는 성공했지만 멤버 INSERT 가 실패해 같은 트랜잭션의 방도 롤백됐다.
        assertThat(roomRepository.count()).isZero();
    }

    private CreateRoomRequest request(String nickname) {
        return new CreateRoomRequest(nickname, RoomMode.SPEAKER, PlayerProvider.YOUTUBE);
    }

    private void saveExistingRoom(String code) {
        roomRepository.saveAndFlush(
                Room.create(code, RoomMode.SPEAKER, PlayerProvider.YOUTUBE, UUID.randomUUID(), NOW, TTL));
    }
}
