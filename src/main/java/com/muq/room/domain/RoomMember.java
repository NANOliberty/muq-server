package com.muq.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방에 들어온 참여자 한 명.
 * 참여자는 로그인 여부와 무관하게 participantId(UUID)로 식별한다. 호스트도 role 만 다른 같은 멤버다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "room_member")
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 왜 LAZY: 기본값 EAGER 는 멤버를 조회할 때마다 방까지 조인한다. 필요할 때만 가져온다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private UUID participantId;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(nullable = false)
    private Instant joinedAt;

    private RoomMember(Room room, UUID participantId, String nickname, MemberRole role, Instant now) {
        this.room = room;
        this.participantId = participantId;
        this.nickname = nickname;
        this.role = role;
        this.joinedAt = now;
    }

    public static RoomMember host(Room room, UUID participantId, String nickname, Instant now) {
        return new RoomMember(room, participantId, nickname, MemberRole.HOST, now);
    }
}
