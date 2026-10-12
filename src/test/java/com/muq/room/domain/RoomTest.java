package com.muq.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 방이 열려 있는지 판단하는 규칙을 검증한다.
 * 엔티티의 메서드는 DB 없이도 동작하는 순수 로직이라 스프링 없이 객체만 만들어 테스트한다.
 */
class RoomTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-10T03:00:00Z");
    private static final Duration TTL = Duration.ofHours(6);
    private static final Instant EXPIRES_AT = CREATED_AT.plus(TTL);

    @Test
    @DisplayName("만료 시각 직전까지는 열려 있다")
    void openBeforeExpiry() {
        Room room = newRoom();

        assertThat(room.isOpen(CREATED_AT)).isTrue();
        assertThat(room.isOpen(EXPIRES_AT.minusMillis(1))).isTrue();
    }

    @Test
    @DisplayName("만료 시각 정각부터는 닫힌 것으로 본다")
    void closedFromExpiry() {
        Room room = newRoom();

        assertThat(room.isOpen(EXPIRES_AT)).isFalse();
        assertThat(room.isOpen(EXPIRES_AT.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("상태가 CLOSED면 만료 전이라도 닫힌 방이다")
    void closedByStatus() {
        Room room = newRoom();
        // 방을 닫는 기능(room.close())은 호스트 제어를 만들 때 생긴다. 그 전까지는 상태를 직접 바꿔서 검증한다.
        ReflectionTestUtils.setField(room, "status", RoomStatus.CLOSED);

        assertThat(room.isOpen(CREATED_AT)).isFalse();
    }

    @Test
    @DisplayName("남은 수명은 만료 시각에서 현재 시각을 뺀 값이다")
    void remainingLifetime() {
        Room room = newRoom();

        assertThat(room.remainingLifetime(CREATED_AT)).isEqualTo(TTL);
        assertThat(room.remainingLifetime(CREATED_AT.plus(Duration.ofHours(2)))).isEqualTo(Duration.ofHours(4));
    }

    private Room newRoom() {
        return Room.create("K7M2QX", RoomMode.SPEAKER, PlayerProvider.YOUTUBE, UUID.randomUUID(), CREATED_AT, TTL);
    }
}
