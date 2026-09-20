package com.fitness.backend.exercise.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.common.web.PageResponse;
import com.fitness.backend.exercise.domain.BodyPart;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.exercise.service.ExerciseService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 운동 종목 조회. 명세 5.2~5.3.
 *
 * <p><b>인증 없이도 열려 있다</b>(부록 B). 다만 토큰이 있으면 {@code isFavorite}를
 * 채워 내려준다 — 같은 엔드포인트가 두 가지로 동작하는 것이고, 이것이 JWT 필터가
 * "토큰 없음"을 실패로 취급하면 안 되는 이유다(명세 1.3).
 */
@ApiV1Controller
@RequestMapping("/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    public PageResponse<ExerciseDtos.ExerciseResponse> list(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BodyPart bodyPart,
            @RequestParam(required = false) Equipment equipment,
            @RequestParam(required = false) MeasureType measureType,
            @RequestParam(defaultValue = "false") boolean favorite,
            @PageableDefault(size = 20, sort = "nameKo", direction = Sort.Direction.ASC)
            Pageable pageable) {

        Long userId = principal == null ? null : principal.userId();
        return PageResponse.from(
                exerciseService.search(userId, blankToNull(q), bodyPart, equipment,
                        measureType, favorite, pageable));
    }

    @GetMapping("/{exerciseId}")
    public ExerciseDtos.ExerciseResponse detail(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long exerciseId) {
        Long userId = principal == null ? null : principal.userId();
        return exerciseService.get(userId, exerciseId);
    }

    /** 빈 문자열은 "검색어 없음"으로 본다 — {@code ?q=}로 비워 보내는 화면 동작을 받아준다. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
