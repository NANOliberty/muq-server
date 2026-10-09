package com.muq.room.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muq.room.domain.MemberRole;
import com.muq.room.domain.PlayerProvider;
import com.muq.room.domain.Room;
import com.muq.room.domain.RoomMember;
import com.muq.room.domain.RoomMode;
import com.muq.room.domain.RoomStatus;
import com.muq.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 방·멤버가 실제 PostgreSQL 에 저장되고, DB 제약이 중복을 막는지 검증한다.
 * 방 생성의 재시도 로직은 "코드가 겹치면 DataIntegrityViolationException 이 난다"는 사실에 기대므로 여기서 먼저 확인한다.
 */
class RoomRepositoryTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");
    private static final Duration TTL = Duration.ofHours(6);

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    // 왜 직접 지우나: 컨테이너를 모든 테스트가 공유한다. 남은 행이 다른 테스트의 유니크 제약에 걸리지 않게 한다.
    @AfterEach
    void cleanUp() {
        roomMemberRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("방을 저장하면 ACTIVE 상태이고 만료 시각은 생성 시각 + TTL이다")
    void saveRoom() {
        Room saved = roomRepository.saveAndFlush(newRoom("AAAAAA"));

        Room found = roomRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCode()).isEqualTo("AAAAAA");
        assertThat(found.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(found.getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    @DisplayName("같은 코드의 방을 또 저장하면 유니크 제약 위반이 난다")
    void duplicateCode() {
        roomRepository.saveAndFlush(newRoom("BBBBBB"));

        assertThatThrownBy(() -> roomRepository.saveAndFlush(newRoom("BBBBBB")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("호스트 멤버를 저장하면 HOST 역할로 조회된다")
    void saveHostMember() {
        Room room = roomRepository.saveAndFlush(newRoom("CCCCCC"));
        UUID participantId = room.getHostId();

        RoomMember saved = roomMemberRepository.saveAndFlush(RoomMember.host(room, participantId, "나노", NOW));

        RoomMember found = roomMemberRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getRole()).isEqualTo(MemberRole.HOST);
        assertThat(found.getParticipantId()).isEqualTo(participantId);
    }

    @Test
    @DisplayName("같은 참여자를 한 방에 두 번 저장하면 유니크 제약 위반이 난다")
    void duplicateMember() {
        Room room = roomRepository.saveAndFlush(newRoom("DDDDDD"));
        UUID participantId = room.getHostId();
        roomMemberRepository.saveAndFlush(RoomMember.host(room, participantId, "나노", NOW));

        assertThatThrownBy(() ->
                roomMemberRepository.saveAndFlush(RoomMember.host(room, participantId, "나노", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Room newRoom(String code) {
        return Room.create(code, RoomMode.SPEAKER, PlayerProvider.YOUTUBE, UUID.randomUUID(), NOW, TTL);
    }
}
