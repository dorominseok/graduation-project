package com.fitness.backend.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.common.web.ApiV1Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 에러 응답이 명세 1.5의 단일 구조를 지키는지, {@code /api/v1} 접두사가 실제로
 * 붙는지 확인한다. 시험용 컨트롤러를 띄워 HTTP 계층까지 통과시킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @TestConfiguration
    @ApiV1Controller
    @RequestMapping("/__test")
    static class TestController {

        record Body(@NotBlank String nickname, @Min(1) int reps) {
        }

        @PostMapping("/echo")
        String echo(@Valid @RequestBody Body body) {
            return body.nickname();
        }

        @PostMapping("/boom")
        String boom() {
            throw new ApiException(ErrorCode.DRAFT_SESSION_EXISTS, "진행 중인 운동이 있어요.");
        }

        @PostMapping("/blowup")
        String blowup() {
            throw new IllegalStateException("DB password is hunter2");
        }
    }

    @Test
    @DisplayName("컨트롤러가 /api/v1 아래에 붙는다 — 접두사 없는 경로는 404")
    void prefixApplied() throws Exception {
        mvc.perform(post("/__test/boom")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/__test/boom")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ApiException은 code와 메시지를 그대로 싣는다")
    void apiException() throws Exception {
        mvc.perform(post("/api/v1/__test/boom"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_SESSION_EXISTS"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("진행 중인 운동이 있어요."))
                .andExpect(jsonPath("$.path").value("/api/v1/__test/boom"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("본문 검증 실패는 필드별 사유를 errors에 담는다")
    void bodyValidation() throws Exception {
        mvc.perform(post("/api/v1/__test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\",\"reps\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    @DisplayName("필드 오류가 아니면 errors 키 자체를 생략한다 (명세 1.5)")
    void omitsErrorsWhenNotFieldLevel() throws Exception {
        mvc.perform(post("/api/v1/__test/boom"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("예상치 못한 예외는 내부 사정을 노출하지 않는다")
    void hidesInternalDetail() throws Exception {
        mvc.perform(post("/api/v1/__test/blowup"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("일시적인 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("없는 경로도 명세의 RESOURCE_NOT_FOUND로 통일한다")
    void unknownPath() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("actuator는 접두사 영향을 받지 않는다 (부록 B)")
    void actuatorUnaffected() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
