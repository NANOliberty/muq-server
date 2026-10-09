package com.muq.global.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 알리는 단 하나의 예외.
 * 예외 클래스를 상황마다 만들지 않고 ErrorCode 로 구분한다. 그래야 핸들러가 하나로 끝나고 에러 목록이 한 파일에 모인다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
