package com.muq.room.service;

import com.muq.auth.service.GuestSessionService;
import com.muq.global.error.BusinessException;
import com.muq.global.error.ErrorCode;
import com.muq.room.config.RoomProperties;
import com.muq.room.domain.MemberRole;
import com.muq.room.domain.Room;
import com.muq.room.domain.RoomMember;
import com.muq.room.dto.CreateRoomRequest;
import com.muq.room.dto.JoinRoomRequest;
import com.muq.room.dto.RoomEntryResponse;
import com.muq.room.dto.RoomResponse;
import com.muq.room.dto.SessionResponse;
import com.muq.room.repository.RoomMemberRepository;
import com.muq.room.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방의 생성과 수명을 다룬다.
 * 방 생성은 "겹치지 않는 코드로 방과 호스트를 저장(PostgreSQL)하고, 호스트의 세션을 발급(Redis)한다"까지다.
 * 조회와 입장은 열려 있는 방(ACTIVE 이고 만료 전)에만 허용한다.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomCodeGenerator roomCodeGenerator;
    private final GuestSessionService guestSessionService;
    private final RoomProperties roomProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // 왜 @Transactional 이 없나: 코드가 겹치면 그 트랜잭션은 롤백 전용이 되어 같은 트랜잭션 안에서는
    //   다시 저장할 수 없다. 그래서 시도 한 번이 트랜잭션 하나가 되도록 saveWithUniqueCode 안에서 경계를 직접 긋는다.
    // 왜 DB 다음에 Redis 인가: 세션에는 확정된 방 코드가 들어가야 한다. 세션 발급이 실패하면 500 으로 끝나고,
    //   들어갈 수 없는 방은 만료 시각이 지나면 정리 대상이 된다. 두 저장소를 하나의 트랜잭션으로 묶지 않는다.
    public RoomEntryResponse create(CreateRoomRequest request) {
        UUID hostId = UUID.randomUUID();
        Room room = saveWithUniqueCode(request, hostId);
        String token = guestSessionService.issue(hostId, request.nickname(), room.getCode(), roomProperties.ttl());
        return toEntry(room, token, hostId, request.nickname(), MemberRole.HOST);
    }

    public RoomResponse get(String code) {
        Room room = findOpenRoom(code, clock.instant());
        return RoomResponse.from(room);
    }

    // 왜 @Transactional 이 없나: DB 쓰기가 멤버 INSERT 하나뿐이라 저장소의 트랜잭션으로 충분하다.
    //   순서는 방 생성과 같다. 멤버를 DB 에 남긴 뒤 세션을 발급한다.
    // 왜 TTL 이 remainingLifetime 인가: 6시간을 새로 주면 세션이 방보다 오래 산다. 방이 끝나는 시각에 함께 끝나게 한다.
    public RoomEntryResponse joinAsGuest(String code, JoinRoomRequest request) {
        Instant now = clock.instant();
        Room room = findOpenRoom(code, now);
        UUID participantId = UUID.randomUUID();
        roomMemberRepository.save(RoomMember.guest(room, participantId, request.nickname(), now));
        String token = guestSessionService.issue(
                participantId, request.nickname(), room.getCode(), room.remainingLifetime(now));
        return toEntry(room, token, participantId, request.nickname(), MemberRole.GUEST);
    }

    // 왜 404 와 410 을 나누나: "코드를 잘못 입력했다"와 "방이 이미 끝났다"는 사용자에게 다른 안내가 필요하다.
    private Room findOpenRoom(String code, Instant now) {
        // 왜 대문자로 바꾸나: 코드는 사람이 손으로 입력한다. 저장된 코드는 항상 대문자라서 여기서 맞춘다.
        String normalizedCode = code.toUpperCase(Locale.ROOT);
        Room room = roomRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (!room.isOpen(now)) {
            throw new BusinessException(ErrorCode.ROOM_CLOSED);
        }
        return room;
    }

    private Room saveWithUniqueCode(CreateRoomRequest request, UUID hostId) {
        for (int attempt = 1; attempt <= roomProperties.codeMaxAttempts(); attempt++) {
            String code = roomCodeGenerator.generate();
            try {
                return transactionTemplate.execute(status -> saveRoomAndHost(code, request, hostId));
            } catch (DataIntegrityViolationException e) {
                if (!isCodeConflict(e)) {
                    throw e;
                }
                log.warn("방 코드 충돌, 다시 시도한다. attempt={}", attempt);
            }
        }
        throw new BusinessException(ErrorCode.ROOM_CODE_EXHAUSTED);
    }

    private Room saveRoomAndHost(String code, CreateRoomRequest request, UUID hostId) {
        Instant now = clock.instant();
        Room room = Room.create(
                code, request.mode(), request.playerProvider(), hostId, now, roomProperties.ttl());
        // 왜 saveAndFlush: INSERT 를 이 줄에서 바로 보내 유니크 제약 위반이 여기서 드러나게 한다.
        roomRepository.saveAndFlush(room);
        roomMemberRepository.save(RoomMember.host(room, hostId, request.nickname(), now));
        return room;
    }

    // 왜 제약 이름까지 보나: DataIntegrityViolationException 은 NOT NULL·길이 초과 같은 다른 위반에도 난다.
    //   그런 버그까지 "코드 충돌"로 보고 재시도하면 원인이 ROOM_CODE_EXHAUSTED 뒤에 숨는다.
    private boolean isCodeConflict(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && Room.CODE_UNIQUE_CONSTRAINT.equals(violation.getConstraintName());
    }

    private RoomEntryResponse toEntry(
            Room room, String token, UUID participantId, String nickname, MemberRole role) {
        SessionResponse session = new SessionResponse(token, participantId, nickname, role);
        return new RoomEntryResponse(RoomResponse.from(room), session);
    }
}
