package com.fitness.backend.importing.domain;

import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.importing.domain.ParsedLog.ParsedDay;
import com.fitness.backend.importing.domain.ParsedLog.ParsedExercise;
import com.fitness.backend.importing.domain.ParsedLog.ParsedSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 바디캘린더의 "운동 일지 내보내기" 글을 읽는다(「기록가져오기_설계서」 3장).
 *
 * <p>줄 단위로 읽는다. 날짜 줄이 새 날을 열고, 세트 줄이 아닌 줄이 종목 이름이며, 그 아래 세트 줄이 그 종목의
 * 세트다. 빈 줄은 종목의 끝이다 — 빈 줄이 빠져도 세트 줄 다음의 이름 줄이 새 종목을 연다.
 *
 * <pre>
 * 2026-11-18 바디캘린더 472번째 운동 일지   ← 날짜
 * 유산소1 복부1 등1 가슴1                    ← 부위 요약(버림)
 * 자유 운동 3분 10세트 2.8톤                 ← 전체 요약(N분만 씀)
 * 벤치 프레스: 바벨                          ← 종목 이름
 * 웜업 20kg 10회                             ← 세트
 * 1세트 40kg 10회
 * </pre>
 */
public final class BodyCalendarParser {

    private static final Pattern DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}) 바디캘린더");
    /** 날짜 줄 다음, "낱말+숫자"만 있는 줄 — {@code 가슴2 등3 어깨3} */
    private static final Pattern PARTS = Pattern.compile("^[^\\d ]+\\d+(?: [^\\d ]+\\d+)*$");
    /** {@code 자유 운동 110분 18.4톤}, {@code 자유 운동 3분 10세트 2.8톤}. 앞의 이름은 루틴마다 다를 수 있다 */
    private static final Pattern SUMMARY =
            Pattern.compile("^(?:.+ )?(\\d+)분(?: \\d+세트)?(?: [\\d.,]+(?:톤|kg))?$");
    /** 세트 줄의 머리 — {@code 1세트}, {@code 웜업}, {@code 드랍}. 뒤는 값 */
    private static final Pattern SET = Pattern.compile("^(?:\\d+세트|(웜업|드랍)) (.+)$");
    private static final Pattern WEIGHT_REPS = Pattern.compile("^(\\d+(?:\\.\\d+)?) ?kg (\\d+) ?회$");
    private static final Pattern REPS = Pattern.compile("^(\\d+) ?회$");
    private static final Pattern TIME = Pattern.compile("^(?:(\\d+):)?(\\d{1,2}):(\\d{2})$");
    /** 유산소 구간 — {@code 1구간 9.0km/h 04:30 1}. 이 줄이 있는 종목은 통째로 뺀다(결정 3) */
    private static final Pattern CARDIO = Pattern.compile("^\\d+구간 ");
    /** 이름 앞의 묶음 표시 — {@code (슈퍼세트) 푸시다운} */
    private static final Pattern TAG = Pattern.compile("^\\([^)]*\\) *");

    /** 원래 이름의 최대 길이 — 맞춘 이름 기억(V17)의 칸 크기 */
    public static final int MAX_SOURCE_LENGTH = 200;

    private BodyCalendarParser() {
    }

    /**
     * @throws ApiException 날짜 줄이 하나도 없으면 {@code IMPORT_FORMAT_UNKNOWN}
     */
    public static ParsedLog parse(String text) {
        Map<LocalDate, DayBuilder> days = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        DayBuilder day = null;
        ExerciseBuilder current = null;

        for (String raw : (text == null ? "" : text).lines().toList()) {
            String line = normalize(raw);
            if (line.isEmpty()) {
                current = null;
                continue;
            }

            LocalDate date = dateOf(line);
            if (date != null) {
                day = days.computeIfAbsent(date, DayBuilder::new);
                day.inHeader = true;
                current = null;
                continue;
            }
            if (day == null) {
                unknown.add(line);
                continue;
            }

            Matcher set = SET.matcher(line);
            if (set.matches()) {
                ParsedSet parsed = current == null ? null : setOf(set.group(1), set.group(2));
                if (parsed == null) {
                    unknown.add(line);
                } else {
                    current.sets.add(parsed);
                    day.inHeader = false;
                }
                continue;
            }
            if (CARDIO.matcher(line).find()) {
                if (current == null) {
                    unknown.add(line);
                } else {
                    current.cardio = true;
                    day.inHeader = false;
                }
                continue;
            }

            if (day.inHeader && headerLine(line, day)) {
                continue;
            }
            current = new ExerciseBuilder(line);
            day.exercises.add(current);
        }

        if (days.isEmpty()) {
            throw new ApiException(ErrorCode.IMPORT_FORMAT_UNKNOWN);
        }

        List<ParsedDay> result = days.values().stream()
                .sorted(Comparator.comparing(builder -> builder.date))
                .map(builder -> builder.build(unknown))
                .filter(parsed -> !parsed.exercises().isEmpty())
                .toList();
        return new ParsedLog(result, List.copyOf(unknown));
    }

    /** 줄 앞뒤 공백을 떼고, 줄 안의 공백(다른 앱이 넣는 줄바꿈 없는 공백 포함)을 한 칸으로 */
    private static String normalize(String raw) {
        return raw.replace(' ', ' ').replace('　', ' ').strip().replaceAll("\\s+", " ");
    }

    private static LocalDate dateOf(String line) {
        Matcher matcher = DATE.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 날짜 줄 다음의 요약 줄이면 읽고 true */
    private static boolean headerLine(String line, DayBuilder day) {
        Matcher summary = SUMMARY.matcher(line);
        if (summary.matches()) {
            if (day.minutes == null) {
                day.minutes = parseIntOrNull(summary.group(1));
            }
            return true;
        }
        return PARTS.matcher(line).matches();
    }

    /** 세트 값. 모르는 꼴이거나 저장할 수 없는 값(0회, 0초, 너무 큰 수)이면 null */
    private static ParsedSet setOf(String kindWord, String values) {
        SetKind kind = kindWord == null ? SetKind.SET : kindWord.equals("웜업") ? SetKind.WARMUP : SetKind.DROP;

        Matcher weightReps = WEIGHT_REPS.matcher(values);
        if (weightReps.matches()) {
            BigDecimal weight = new BigDecimal(weightReps.group(1));
            Integer reps = repsOf(weightReps.group(2));
            boolean weightFits = weight.scale() <= 2 && weight.precision() - weight.scale() <= 4;
            return reps == null || !weightFits ? null : new ParsedSet(kind, weight, reps, null);
        }
        Matcher reps = REPS.matcher(values);
        if (reps.matches()) {
            Integer count = repsOf(reps.group(1));
            return count == null ? null : new ParsedSet(kind, null, count, null);
        }
        Matcher time = TIME.matcher(values);
        if (time.matches()) {
            Integer hours = time.group(1) == null ? 0 : parseIntOrNull(time.group(1));
            Integer minutes = parseIntOrNull(time.group(2));
            Integer seconds = parseIntOrNull(time.group(3));
            if (hours == null || minutes == null || seconds == null || seconds >= 60 || hours > 24) {
                return null;
            }
            int total = hours * 3600 + minutes * 60 + seconds;
            return total == 0 ? null : new ParsedSet(kind, null, null, total);
        }
        return null;
    }

    private static Integer repsOf(String digits) {
        Integer value = parseIntOrNull(digits);
        return value == null || value < 1 || value > Short.MAX_VALUE ? null : value;
    }

    private static Integer parseIntOrNull(String digits) {
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class DayBuilder {
        private final LocalDate date;
        private final List<ExerciseBuilder> exercises = new ArrayList<>();
        private Integer minutes;
        /** 날짜 줄 바로 다음, 첫 세트 전 — 요약 줄이 올 자리 */
        private boolean inHeader;

        private DayBuilder(LocalDate date) {
            this.date = date;
        }

        /** 유산소와 세트 없는 이름은 뺀다. 세트 없는 이름은 모르는 줄로 남긴다 */
        private ParsedDay build(List<String> unknown) {
            List<ParsedExercise> parsed = new ArrayList<>();
            for (ExerciseBuilder exercise : exercises) {
                if (exercise.cardio) {
                    continue;
                }
                ParsedExercise built = exercise.build();
                if (built == null) {
                    unknown.add(exercise.title);
                } else {
                    parsed.add(built);
                }
            }
            return new ParsedDay(date, minutes, List.copyOf(parsed));
        }
    }

    private static final class ExerciseBuilder {
        private final String title;
        private final List<ParsedSet> sets = new ArrayList<>();
        private boolean cardio;

        private ExerciseBuilder(String title) {
            this.title = title;
        }

        /** {@code (슈퍼세트)}를 떼고 {@code :} 앞을 이름, 뒤를 쉼표로 나눠 수식어로 */
        private ParsedExercise build() {
            if (sets.isEmpty()) {
                return null;
            }
            String stripped = title;
            Matcher tag = TAG.matcher(stripped);
            while (tag.find()) {
                stripped = stripped.substring(tag.end());
                tag = TAG.matcher(stripped);
            }

            int colon = indexOfColon(stripped);
            String name = (colon < 0 ? stripped : stripped.substring(0, colon)).strip();
            List<String> modifiers = colon < 0 ? List.of() : Arrays.stream(stripped.substring(colon + 1).split(","))
                    .map(String::strip)
                    .filter(word -> !word.isEmpty())
                    .toList();
            if (name.isEmpty()) {
                return null;
            }
            String source = modifiers.isEmpty() ? name : name + ": " + String.join(", ", modifiers);
            if (source.length() > MAX_SOURCE_LENGTH) {
                return null;
            }
            return new ParsedExercise(source, name, modifiers, List.copyOf(sets));
        }

        private static int indexOfColon(String text) {
            int ascii = text.indexOf(':');
            int wide = text.indexOf('：');
            if (ascii < 0) {
                return wide;
            }
            return wide < 0 ? ascii : Math.min(ascii, wide);
        }
    }
}
