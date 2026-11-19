package com.fitness.backend.importing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.importing.BodyCalendarSamples;
import com.fitness.backend.importing.domain.ParsedLog.ParsedDay;
import com.fitness.backend.importing.domain.ParsedLog.ParsedExercise;
import com.fitness.backend.importing.domain.ParsedLog.ParsedSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 바디캘린더 글 읽기(「기록가져오기_설계서」 3장) */
class BodyCalendarParserTest {

    private static final LocalDate D1 = LocalDate.of(2026, 11, 17);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 18);

    @Test
    @DisplayName("샘플 두 개 — 날짜 순, N분, 종목 이름·수식어, 세트 수. 유산소는 빠지고 (슈퍼세트)는 떼어진다")
    void samples() {
        // 뒤 날짜를 먼저 붙여넣어도 날짜 순이다
        ParsedLog log = BodyCalendarParser.parse(BodyCalendarSamples.second(D2) + "\n" + BodyCalendarSamples.first(D1));

        assertThat(log.days()).extracting(ParsedDay::date).containsExactly(D1, D2);
        assertThat(log.days()).extracting(ParsedDay::minutes).containsExactly(110, 3);
        assertThat(log.unknownLines()).isEmpty();

        ParsedDay first = log.days().get(0);
        assertThat(first.exercises()).extracting(ParsedExercise::source).containsExactly(
                "월 슬라이드", "풀업: 맨몸", "랫풀다운: 오버핸드", "랫풀다운: 언더핸드", "벤치 프레스: 바벨",
                "숄더 프레스: 스탠딩, 원암, 덤벨", "덤벨 컬: 번갈아가며, 인클라인", "트라이셉스 익스텐션: 스탠딩, 덤벨",
                "푸시다운", "페이스 풀: 케이블", "사이드 레터럴 레이즈: 핀 머신");
        assertThat(first.exercises()).extracting(exercise -> exercise.sets().size())
                .containsExactly(3, 10, 3, 3, 9, 6, 8, 3, 3, 3, 3);
        ParsedExercise shoulder = first.exercises().get(5);
        assertThat(shoulder.name()).isEqualTo("숄더 프레스");
        assertThat(shoulder.modifiers()).containsExactly("스탠딩", "원암", "덤벨");

        ParsedDay second = log.days().get(1);
        assertThat(second.exercises()).extracting(ParsedExercise::source)
                .containsExactly("플랭크: 기본", "풀업: 중량", "벤치 프레스: 바벨");
        assertThat(second.exercises()).extracting(exercise -> exercise.sets().size()).containsExactly(3, 5, 4);
    }

    @Test
    @DisplayName("세트 값 — kg·회, 회만, 시간(초). 맨몸 kg도 읽어 둔다(버리는 것은 저장 때). 웜업·드랍은 종류로")
    void setValues() {
        ParsedLog log = BodyCalendarParser.parse(BodyCalendarSamples.first(D1) + "\n" + BodyCalendarSamples.second(D2));
        List<ParsedExercise> first = log.days().get(0).exercises();
        List<ParsedExercise> second = log.days().get(1).exercises();

        assertThat(first.get(0).sets().get(0)).isEqualTo(new ParsedSet(SetKind.SET, null, 30, null));
        assertThat(first.get(1).sets().get(0)).isEqualTo(new ParsedSet(SetKind.SET, new BigDecimal("81"), 19, null));
        assertThat(second.get(0).sets()).containsOnly(new ParsedSet(SetKind.SET, null, null, 120));
        assertThat(second.get(2).sets()).extracting(ParsedSet::kind)
                .containsExactly(SetKind.WARMUP, SetKind.DROP, SetKind.SET, SetKind.SET);
        assertThat(second.get(2).sets().get(0).weightKg()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("실패 세트는 해낸 횟수로 — '실패 35kg 5/10회'는 35kg 5회. 목표가 붙은 횟수는 어느 세트든 앞의 수, 0회 실패는 버림")
    void failureSets() {
        String text = BodyCalendarSamples.one(D1, "숄더 프레스: 앉아서, 핀 머신",
                "1세트 35kg 7회", "2세트 35kg 8/10회", "실패 35kg 5/10회", "실패 35kg 0/10회", "실패 12/15회");
        ParsedLog log = BodyCalendarParser.parse(text);
        List<ParsedSet> sets = log.days().get(0).exercises().get(0).sets();

        assertThat(sets).containsExactly(
                new ParsedSet(SetKind.SET, new BigDecimal("35"), 7, null),
                new ParsedSet(SetKind.SET, new BigDecimal("35"), 8, null),
                new ParsedSet(SetKind.FAIL, new BigDecimal("35"), 5, null),
                new ParsedSet(SetKind.FAIL, null, 12, null));
        assertThat(log.unknownLines()).containsExactly("실패 35kg 0/10회");
    }

    @Test
    @DisplayName("날짜 줄이 없으면 IMPORT_FORMAT_UNKNOWN")
    void unknownFormat() {
        assertThatThrownBy(() -> BodyCalendarParser.parse("오늘 벤치 3세트 했음\n1세트 40kg 10회"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IMPORT_FORMAT_UNKNOWN));
        assertThatThrownBy(() -> BodyCalendarParser.parse("")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("CRLF·줄바꿈 없는 공백·빈 줄 빠짐도 읽는다 — 세트 줄 다음의 이름 줄이 새 종목")
    void looseWhitespace() {
        String text = "2026-11-18 바디캘린더 1번째 운동 일지\r\n"
                + "자유 운동 40분 6세트 1.2톤\r\n"
                + "풀업: 중량\r\n"
                + "1세트  40kg 10회\r\n"
                + "덤벨 컬\r\n"
                + "1세트 10kg 12회\r\n";
        ParsedDay day = BodyCalendarParser.parse(text).days().get(0);

        assertThat(day.minutes()).isEqualTo(40);
        assertThat(day.exercises()).extracting(ParsedExercise::source).containsExactly("풀업: 중량", "덤벨 컬");
        assertThat(day.exercises().get(0).sets()).hasSize(1);
    }

    @Test
    @DisplayName("모르는 줄은 건너뛰고 남긴다. 같은 날짜는 한 날로 합치고, 유산소만 있는 날은 빠진다")
    void unknownLinesAndMerging() {
        String text = BodyCalendarSamples.one(D1, "벤치 프레스: 바벨", "1세트 40kg 10회", "2세트 90lb 10회")
                + "\n" + BodyCalendarSamples.one(D1, "덤벨 컬", "1세트 10kg 12회")
                + "\n" + BodyCalendarSamples.one(D2, "런닝: 트레드밀", "1구간 9.0km/h 04:30 1");
        ParsedLog log = BodyCalendarParser.parse(text);

        assertThat(log.days()).hasSize(1);
        assertThat(log.days().get(0).exercises()).extracting(ParsedExercise::source)
                .containsExactly("벤치 프레스: 바벨", "덤벨 컬");
        assertThat(log.days().get(0).exercises().get(0).sets()).hasSize(1);
        assertThat(log.unknownLines()).containsExactly("2세트 90lb 10회");
    }
}
