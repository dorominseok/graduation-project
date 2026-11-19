package com.fitness.backend.importing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.exercise.domain.BrowseCategory;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.exercise.service.ExerciseService;
import com.fitness.backend.exercise.web.ExerciseDtos.CustomExerciseRequest;
import com.fitness.backend.importing.domain.SetKind;
import com.fitness.backend.importing.web.ImportDtos.ImportDay;
import com.fitness.backend.importing.web.ImportDtos.ImportExercise;
import com.fitness.backend.importing.web.ImportDtos.ImportRequest;
import com.fitness.backend.importing.web.ImportDtos.ImportSet;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 하루라도 막히면 아무것도 넣지 않는지 — 실제로 커밋·롤백되는지 본다.
 *
 * <p>{@code ImportApiTest}는 테스트 전체가 한 트랜잭션이라, 저장이 실패해도 그 안에서는 앞서 넣은 날이 보인다.
 * 여기서는 테스트에 트랜잭션을 걸지 않고 결과를 SQL로 읽는다. 롤백에 기대지 않으므로 만든 계정은 끝나고 지운다.
 */
@SpringBootTest
class ImportCommitTest {

    @Autowired ImportService importService;
    @Autowired AuthService authService;
    @Autowired ExerciseService exerciseService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : users) {
            // 세트가 내 종목을 가리킬 수 있어 하루 기록을 먼저 지운다(「내종목_설계서」 4장)
            jdbc.update("delete from workout_sessions where user_id = ?", userId);
            jdbc.update("delete from users where id = ?", userId);
        }
    }

    @Test
    @DisplayName("둘째 날에 남의 내 종목이 있으면 404이고, 첫째 날도 짝도 남지 않는다")
    void allOrNothing() {
        long me = signUp();
        long other = signUp();
        long othersCustom = exerciseService.create(other, new CustomExerciseRequest("남의 종목", BrowseCategory.BACK,
                null, Equipment.CABLE, MeasureType.WEIGHT_REPS, null)).id();
        long bench = jdbc.queryForObject("select id from exercises where owner_id is null and name_ko = '바벨 벤치프레스'",
                Long.class);
        LocalDate today = LocalDate.now(clock);
        ImportSet set = new ImportSet(SetKind.SET, new BigDecimal("60"), 10, null);
        ImportRequest request = new ImportRequest(List.of(
                new ImportDay(today.minusDays(2), 40, List.of(new ImportExercise("벤치 프레스: 바벨", bench, true,
                        List.of(set)))),
                new ImportDay(today.minusDays(1), 40, List.of(new ImportExercise("남의 종목", othersCustom, true,
                        List.of(set))))));

        assertThatThrownBy(() -> importService.save(me, request))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        assertThat(jdbc.queryForObject("select count(*) from workout_sessions where user_id = ?", Long.class, me))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from exercise_import_aliases where user_id = ?", Long.class,
                me)).isZero();
    }

    private long signUp() {
        String email = "ic" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        long id = authService.signUp(email, "hunter2hunter2", "민석").user().getId();
        users.add(id);
        return id;
    }
}
