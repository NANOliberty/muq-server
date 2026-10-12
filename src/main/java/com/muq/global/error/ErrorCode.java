package com.muq.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서비스가 의도적으로 내보내는 모든 에러의 목록.
 * 클라이언트는 응답의 code(= enum 이름)로 분기하고, message 는 사람에게 보여 줄 기본 문구다.
 * 도메인 에러는 해당 기능을 만들 때 여기에 추가한다. 미리 넣어 두지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다. 코드를 확인해 주세요."),

    // 왜 404 가 아니라 410: 방이 있었지만 끝났다는 뜻이다. 클라이언트가 "코드 오타"와 "종료된 방"을 다르게 안내할 수 있다.
    ROOM_CLOSED(HttpStatus.GONE, "이미 종료된 방입니다."),

    // 왜 503: 요청이 잘못된 것이 아니라 서버가 지금 코드를 못 만든 것이다. 같은 요청을 다시 보내면 성공할 수 있다.
    ROOM_CODE_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "방 코드를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
