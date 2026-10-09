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
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
