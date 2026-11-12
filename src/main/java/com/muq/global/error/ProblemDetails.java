package com.muq.global.error;

import org.springframework.http.ProblemDetail;

/**
 * ErrorCode 를 RFC 9457 Problem Details 로 바꾸는 단 한 곳.
 * 컨트롤러 예외(GlobalExceptionHandler)와 보안 필터 예외(ProblemDetailSecurityErrorHandler)가 함께 쓴다.
 * 두 곳이 따로 만들면 언젠가 응답 모양이 어긋난다.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode errorCode) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problem.setProperty("code", errorCode.name());
        return problem;
    }
}
