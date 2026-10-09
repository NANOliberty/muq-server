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
import lombok.Getter;

/**
 * 공유 큐가 열리는 방.
 * 방의 정체(코드, 모드, 호스트, 플레이어)와 수명(상태, 만료 시각)을 가진다. 큐 내용은 Redis 에 있다.
 * setter 가 없으므로 생성은 create(), 상태 변경은 의도가 드러나는 메서드로만 한다.
 */
@Getter
@Entity
@Table(name = "room")
public class Room {

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

    // 왜 protected: JPA 가 조회 결과로 객체를 만들 때 기본 생성자가 필요하다. 밖에서는 create() 만 쓰게 막는다.
    protected Room() {
    }

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
}
