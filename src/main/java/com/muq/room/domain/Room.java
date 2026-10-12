package com.muq.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공유 큐가 열리는 방.
 * 방의 정체(코드, 모드, 호스트, 플레이어)와 수명(상태, 만료 시각)을 가진다. 큐 내용은 Redis 에 있다.
 * setter 가 없으므로 생성은 create(), 상태 변경은 의도가 드러나는 메서드로만 한다.
 */
@Getter
// 왜 PROTECTED: JPA 가 조회 결과로 객체를 만들 때 기본 생성자가 필요하다. 밖에서는 create() 만 쓰게 막는다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "room")
public class Room {

    /** code 유니크 제약의 이름. V1__create_room.sql 과 같아야 한다. 코드 충돌을 다른 제약 위반과 구분하는 데 쓴다. */
    public static final String CODE_UNIQUE_CONSTRAINT = "uk_room_code";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6, unique = true)
    private String code;

    // 왜 STRING: 기본값 ORDINAL 은 enum 순서를 숫자로 저장한다. 중간에 값을 끼워 넣으면 기존 데이터의 의미가 바뀐다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomMode mode;

    @Column(nullable = false)
    private UUID hostId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerProvider playerProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Room(String code, RoomMode mode, PlayerProvider playerProvider, UUID hostId, Instant now, Duration ttl) {
        this.code = code;
        this.mode = mode;
        this.playerProvider = playerProvider;
        this.hostId = hostId;
        this.status = RoomStatus.ACTIVE;
        this.createdAt = now;
        this.expiresAt = now.plus(ttl);
    }

    // 왜 now 를 인자로 받나: 엔티티가 Instant.now() 를 직접 부르면 테스트에서 시간을 고정할 수 없다.
    public static Room create(
            String code, RoomMode mode, PlayerProvider playerProvider, UUID hostId, Instant now, Duration ttl) {
        return new Room(code, mode, playerProvider, hostId, now, ttl);
    }

    // 왜 status 만 보지 않나: 만료 시각이 지나도 status 를 바꿔 주는 배치가 없다. 시각을 직접 비교해야 항상 정확하다.
    //   만료 시각 정각은 이미 끝난 것으로 본다.
    public boolean isOpen(Instant now) {
        return status == RoomStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /** 방이 끝날 때까지 남은 시간. 이 방에 속한 세션의 TTL 로 쓴다. */
    public Duration remainingLifetime(Instant now) {
        return Duration.between(now, expiresAt);
    }
}
