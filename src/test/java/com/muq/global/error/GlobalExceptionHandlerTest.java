package com.muq.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외가 약속한 Problem Details 형식으로 나가는지 검증한다.
 * 아직 도메인 컨트롤러가 없으므로 일부러 예외를 던지는 테스트 전용 컨트롤러를 쓴다.
 * 웹 계층만 올리는 @WebMvcTest 라서 DB·Redis 컨테이너가 필요 없다.
 */
@WebMvcTest(GlobalExceptionHandlerTest.FailingController.class)
@Import(GlobalExceptionHandlerTest.FailingController.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BusinessException은 ErrorCode의 상태 코드와 code로 응답한다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.INVALID_INPUT.getMessage()));
    }

    @Test
    @DisplayName("요청 검증에 실패하면 어느 필드가 틀렸는지 errors에 담아 400으로 응답한다")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500이고 내부 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    @Test
    @DisplayName("없는 경로는 스프링 기본 처리 그대로 404 Problem Details로 응답한다")
    void notFound() throws Exception {
        mockMvc.perform(get("/test/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody NameRequest request) {
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    record NameRequest(@NotBlank String name) {
    }
}
