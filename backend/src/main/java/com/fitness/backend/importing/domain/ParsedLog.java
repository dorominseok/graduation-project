package com.fitness.backend.importing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 붙여넣은 글을 읽은 결과. 아직 우리 종목에 맞추기 전이다.
 *
 * @param days         날짜 순. 같은 날짜가 두 번 나오면 한 날로 합친다
 * @param unknownLines 읽지 못하고 건너뛴 줄 — 바디캘린더 형식이 바뀌면 알아채려고 서버 로그에 남긴다
 */
public record ParsedLog(List<ParsedDay> days, List<String> unknownLines) {

    /**
     * 하루.
     *
     * @param minutes 요약 줄의 {@code N분}. 없으면 null
     */
    public record ParsedDay(LocalDate date, Integer minutes, List<ParsedExercise> exercises) {
    }

    /**
     * 종목 하나.
     *
     * @param source    원래 이름 전체(띄어쓰기 정리, {@code (슈퍼세트)} 뗌) — 예: {@code 풀업: 중량}
     * @param name      {@code :} 앞 이름 — 예: {@code 풀업}
     * @param modifiers {@code :} 뒤 수식어를 쉼표로 나눈 것 — 예: {@code [중량]}
     */
    public record ParsedExercise(String source, String name, List<String> modifiers, List<ParsedSet> sets) {
    }

    /** 세트 하나. 글에 적힌 값 그대로다 — 맞춘 종목이 쓰지 않는 값은 저장할 때 버린다 */
    /** @param targetReps 실패 세트의 목표 횟수(`5/10회`의 10). 실패가 아니거나 목표가 없으면 null */
    public record ParsedSet(SetKind kind, BigDecimal weightKg, Integer reps, Integer durationSec, Integer targetReps) {

        public ParsedSet(SetKind kind, BigDecimal weightKg, Integer reps, Integer durationSec) {
            this(kind, weightKg, reps, durationSec, null);
        }
    }
}
