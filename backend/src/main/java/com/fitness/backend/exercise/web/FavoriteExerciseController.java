package com.fitness.backend.exercise.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.exercise.service.ExerciseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 즐겨찾기 토글. 명세 5.4.
 *
 * <p>경로가 {@code /users/me} 아래인 것은 이 자원의 주인이 사용자이기 때문이다 —
 * 종목은 읽기 전용 공용 데이터고(5.1), 별표는 그 사용자의 것이다.
 *
 * <p>둘 다 <b>멱등</b>이다. 이미 등록/해제된 상태로 다시 불러도 {@code 204}다.
 * 별표는 연타되기 쉬워서, 두 번째 요청이 오류가 되면 화면이 실제 상태와 어긋난다.
 */
@ApiV1Controller
@RequestMapping("/users/me/favorite-exercises")
public class FavoriteExerciseController {

    private final ExerciseService exerciseService;

    public FavoriteExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @Operation(summary = "즐겨찾기 추가")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "추가됨 — 이미 있어도 성공이다")
    })
    @PutMapping("/{exerciseId}")
    public ResponseEntity<Void> add(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long exerciseId) {
        exerciseService.addFavorite(principal.userId(), exerciseId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "즐겨찾기 해제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "해제됨 — 이미 없어도 성공이다")
    })
    @DeleteMapping("/{exerciseId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @PathVariable Long exerciseId) {
        exerciseService.removeFavorite(principal.userId(), exerciseId);
        return ResponseEntity.noContent().build();
    }
}
