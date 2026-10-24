package com.fitness.backend.routine.llm;

import static com.fitness.backend.analysis.domain.MuscleGroup.BACK;
import static com.fitness.backend.analysis.domain.MuscleGroup.BICEPS;
import static com.fitness.backend.exercise.domain.Equipment.BARBELL;
import static com.fitness.backend.exercise.domain.Equipment.CABLE;
import static com.fitness.backend.exercise.domain.Equipment.DUMBBELL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.routine.llm.LlmRoutineRequest.Candidate;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Slot;
import com.fitness.backend.routine.llm.LlmRoutineValidator.Pick;
import com.fitness.backend.routine.llm.LlmRoutineValidator.Result;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** LLM 출력 검증 L1~L6. 설계서 6.1. */
class LlmRoutineValidatorTest {

    private static final JsonMapper JSON = JsonMapper.shared();

    /** 등 5세트(후보 20·23), 이두 3세트(후보 83) */
    private static final LlmRoutineRequest REQUEST = new LlmRoutineRequest(List.of(
            new Slot(BACK, 5, List.of(
                    new Candidate(20, "랫풀다운: 와이드 그립", CABLE, false, true),
                    new Candidate(23, "바벨 로우", BARBELL, false, true))),
            new Slot(BICEPS, 3, List.of(new Candidate(83, "덤벨 컬", DUMBBELL, false, false)))));

    private static final String GOOD_COMMENT = "등은 케이블과 바벨로 당기는 방향을 바꿔 가며 하고, 덤벨 컬로 팔을 마무리하는 순서예요.";

    private static Result validate(String items, String comment) {
        return LlmRoutineValidator.validate(REQUEST, "{\"items\":[" + items + "],\"comment\":\"" + comment + "\"}", JSON);
    }

    private static String pick(String slot, String id) {
        return "{\"slot\":\"" + slot + "\",\"exerciseId\":\"" + id + "\"}";
    }

    @Test
    @DisplayName("후보 안에서 모든 슬롯을 채우고 설명이 깨끗하면 통과 — 응답 순서를 그대로 쓴다")
    void passes() {
        Result result = validate(pick("BACK", "20") + "," + pick("BACK", "23") + "," + pick("BICEPS", "83"), GOOD_COMMENT);

        assertTrue(result.failures().isEmpty(), result.failures().toString());
        assertEquals(List.of(new Pick(BACK, 20), new Pick(BACK, 23), new Pick(BICEPS, 83)), result.picks());
        assertEquals(GOOD_COMMENT, result.comment());
    }

    @Test
    @DisplayName("L1 — JSON이 깨졌거나 모양이 다르면")
    void l1() {
        assertEquals(Set.of("L1"), LlmRoutineValidator.validate(REQUEST, "{\"items\":[", JSON).codes());
        assertEquals(Set.of("L1"), LlmRoutineValidator.validate(REQUEST, "{\"items\":[]}", JSON).codes());
        assertEquals(Set.of("L1"), LlmRoutineValidator.validate(REQUEST, "{\"items\":[{\"slot\":\"BACK\"}],\"comment\":\"\"}",
                JSON).codes());
    }

    @Test
    @DisplayName("L2 — 다른 슬롯의 후보, 없는 슬롯")
    void l2() {
        Result wrongSlot = validate(pick("BACK", "83") + "," + pick("BICEPS", "83"), GOOD_COMMENT);
        Result unknownSlot = validate(pick("BACK", "20") + "," + pick("CHEST", "5") + "," + pick("BICEPS", "83"),
                GOOD_COMMENT);

        assertTrue(wrongSlot.codes().contains("L2"));
        assertEquals(Set.of("L2"), unknownSlot.codes());
    }

    @Test
    @DisplayName("L3 — 빠진 슬롯, 한 슬롯에 셋 이상")
    void l3() {
        assertEquals(Set.of("L3"), validate(pick("BACK", "20"), GOOD_COMMENT).codes());
    }

    @Test
    @DisplayName("L4 — 같은 종목 두 번")
    void l4() {
        assertTrue(validate(pick("BACK", "20") + "," + pick("BACK", "20") + "," + pick("BICEPS", "83"), GOOD_COMMENT)
                .codes().contains("L4"));
    }

    @Test
    @DisplayName("L5 — 4세트 슬롯을 두 종목으로 나눔. 규칙과 같이 5세트부터다")
    void l5() {
        LlmRoutineRequest threeSets = new LlmRoutineRequest(List.of(new Slot(BACK, 4, REQUEST.slots().get(0).candidates())));

        Result result = LlmRoutineValidator.validate(threeSets,
                "{\"items\":[" + pick("BACK", "20") + "," + pick("BACK", "23") + "],\"comment\":\"" + GOOD_COMMENT + "\"}",
                JSON);

        assertEquals(Set.of("L5"), result.codes());
    }

    @Test
    @DisplayName("L6 — 숫자·말로 쓴 수량·처방·의학 표현·길이. 선택은 쓸 만하다고 본다")
    void l6() {
        String picks = pick("BACK", "20") + "," + pick("BICEPS", "83");
        for (String bad : List.of(
                "등을 4세트로 충분히 하는 순서예요.",
                "등은 두 세트씩 나눠서 하는 순서예요.",
                "등부터 시작하세요.",
                "부상을 막으려고 가벼운 컬을 마지막에 뒀어요.",
                "가".repeat(121))) {
            Result result = validate(picks, bad);
            assertEquals(Set.of("L6"), result.codes(), bad);
            assertTrue(result.selectionOk());
            assertFalse(result.commentOk());
        }
    }

    @Test
    @DisplayName("L6 — \"등의 랫풀다운\"을 \"정의 랫풀다운\"으로 쓴 것은 잡고, 라벨 일부(뒤허벅지 등)는 통과")
    void l6WrongBodyWord() {
        String picks = pick("BACK", "20") + "," + pick("BICEPS", "83");

        // 실제로 나온 문장 꼴 — 종목 이름을 줄여 썼다
        assertEquals(Set.of("L6"), validate(picks, "가슴부터 하고, 정의 랫풀다운과 이두의 덤벨 컬로 마무리했어요.").codes());
        assertTrue(validate(picks, "등의 랫풀다운: 와이드 그립과 팔의 덤벨 컬 순서예요.").failures().isEmpty());
        assertTrue(validate(picks, "어깨 전면의 랫풀다운 뒤에 이두의 컬로 마무리했어요.").failures().isEmpty());
    }

    @Test
    @DisplayName("L6 — '한 번에' 같은 일상 표현은 막지 않는다")
    void l6AllowsEverydayWords() {
        assertTrue(validate(pick("BACK", "20") + "," + pick("BICEPS", "83"),
                "등과 이두를 한 번에 이어서 당기는 날이에요.").failures().isEmpty());
    }
}
