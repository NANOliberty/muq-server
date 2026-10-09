package com.muq.global.error;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 컨트롤러의 예외를 RFC 9457 Problem Details 응답으로 바꾼다.
 * 우리가 던진 BusinessException, 요청 검증 실패, 예상하지 못한 예외를 각각 처리한다.
 * 404·405 같은 스프링 MVC 기본 예외는 부모 클래스가 같은 형식으로 처리한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        return toProblemDetail(ex.getErrorCode());
    }

    // 왜 override: 부모의 기본 응답에는 어느 필드가 왜 틀렸는지가 없다. 클라이언트가 입력란별로 표시할 수 있게 errors 를 붙인다.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<InvalidField> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new InvalidField(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = toProblemDetail(ErrorCode.INVALID_INPUT);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // 왜: 예상하지 못한 예외의 메시지에는 SQL·내부 경로 같은 정보가 섞일 수 있다.
    //   원인은 로그에만 남기고 클라이언트에는 고정 문구만 보낸다.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        logger.error("처리되지 않은 예외", ex);
        return toProblemDetail(ErrorCode.INTERNAL_ERROR);
    }

    private ProblemDetail toProblemDetail(ErrorCode errorCode) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problem.setProperty("code", errorCode.name());
        return problem;
    }

    private record InvalidField(String field, String message) {
    }
}
