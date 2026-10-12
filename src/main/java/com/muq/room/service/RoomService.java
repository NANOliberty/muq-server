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
        return toResponse(room, request.nickname(), token);
    }

    // 본문은 도메인·저장소 레이어(isOpen, findByCode)를 만든 뒤 채운다.
    public RoomResponse get(String code) {
        throw new UnsupportedOperationException("방 조회 본문은 아직 구현 전이다");
    }

    public RoomEntryResponse joinAsGuest(String code, JoinRoomRequest request) {
        throw new UnsupportedOperationException("게스트 입장 본문은 아직 구현 전이다");
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

    private RoomEntryResponse toResponse(Room room, String nickname, String token) {
        SessionResponse session = new SessionResponse(token, room.getHostId(), nickname, MemberRole.HOST);
        return new RoomEntryResponse(RoomResponse.from(room), session);
    }
}
