package com.fitness.backend.importing.domain;

import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.importing.domain.ParsedLog.ParsedExercise;
import com.fitness.backend.importing.domain.ParsedLog.ParsedSet;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 원래 이름을 우리 종목에 맞춘다(「기록가져오기_설계서」 4장). 앞 순서에서 맞으면 거기서 끝난다.
 *
 * <ol>
 *   <li>내가 맞춘 짝(V17)
 *   <li>이름이 똑같음 — 원래 이름 전체, 또는 수식어가 없거나 뜻 없는 말뿐이면 {@code :} 앞 이름
 *   <li>미리 만든 짝 표({@code data/import_bodycalendar.csv})
 *   <li>계열 + 수식어 → 비슷한 종목
 *   <li>미등록 운동
 * </ol>
 *
 * <p>후보는 그 사용자에게 보이는 종목뿐이고, 세트를 담을 수 있는 기록 방식이어야 한다. 1~3번은 근거가 분명해
 * 세트를 저장할 수만 있으면 되고, 짐작인 4번은 더 좁게 본다 — kg가 적힌 세트를 맨몸 종목으로 짐작하지 않는다
 * (수식어에 "맨몸"이 있으면 본다).
 */
public final class ExerciseMatcher {

    /** 우리 이름에 쓰는 말로 바꿔 비교하는 수식어(띄어쓰기 뺀 꼴) */
    private static final Map<String, String> MODIFIER_WORDS = Map.of(
            "언더핸드", "언더그립",
            "번갈아가며", "얼터네이트",
            "핀머신", "머신",
            "원판머신", "머신",
            "앉아서", "시티드",
            "T바", "티바");

    /** 이것뿐이면 {@code :} 앞 이름만으로 똑같은지 본다 — {@code 플랭크: 기본}, {@code 푸시업: 맨몸} */
    private static final Set<String> PLAIN_MODIFIERS = Set.of("기본", "스탠딩", "맨몸");

    private static final String BODYWEIGHT_WORD = "맨몸";

    private final Map<Long, Exercise> visibleById;
    private final List<Exercise> visible;
    private final Map<String, Exercise> builtinByName;
    /** {@link SourceName#key}로 묶은 종목 — 내 종목이 앞에 온다 */
    private final Map<String, List<Exercise>> byKey;
    private final Map<String, Long> aliases;
    private final Map<String, String> curated;
    private final List<String> groupsLongestFirst;

    /**
     * @param visible 그 사용자에게 보이는 종목 — 기본 종목과 숨기지 않은 내 종목
     * @param aliases 내가 맞춘 짝. 열쇠는 {@link SourceName#key}, 값은 종목 id
     * @param curated 미리 만든 짝 표. 열쇠는 {@link SourceName#key}, 값은 기본 종목 이름
     */
    public ExerciseMatcher(List<Exercise> visible, Map<String, Long> aliases, Map<String, String> curated) {
        this.visible = List.copyOf(visible);
        this.visibleById = visible.stream().collect(Collectors.toMap(Exercise::getId, Function.identity()));
        this.builtinByName = visible.stream()
                .filter(exercise -> !exercise.isCustom())
                .collect(Collectors.toMap(Exercise::getNameKo, Function.identity()));
        this.byKey = visible.stream()
                .sorted(Comparator.comparing(exercise -> !exercise.isCustom()))
                .collect(Collectors.groupingBy(exercise -> SourceName.key(exercise.getNameKo())));
        this.aliases = Map.copyOf(aliases);
        this.curated = Map.copyOf(curated);
        this.groupsLongestFirst = visible.stream()
                .map(Exercise::getGroupName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparingInt((String group) -> SourceName.compact(group).length()).reversed())
                .toList();
    }

    public record Result(MatchStatus status, Exercise exercise) {
    }

    public Result match(ParsedExercise parsed) {
        Set<MeasureType> savable = savable(parsed.sets());
        String key = SourceName.key(parsed.source());

        Optional<Exercise> found = Optional.ofNullable(aliases.get(key)).map(visibleById::get)
                .filter(exercise -> savable.contains(exercise.getMeasureType()))
                .or(() -> sameName(parsed, key, savable))
                .or(() -> Optional.ofNullable(curated.get(key)).map(builtinByName::get)
                        .filter(exercise -> savable.contains(exercise.getMeasureType())));
        if (found.isPresent()) {
            return new Result(MatchStatus.MATCHED, found.get());
        }
        return similar(parsed)
                .map(exercise -> new Result(MatchStatus.SIMILAR, exercise))
                .orElse(new Result(MatchStatus.UNREGISTERED, null));
    }

    /** 2번. 내 종목을 먼저 본다 — 미리보기에서 만든 내 종목은 원래 이름 그대로다 */
    private Optional<Exercise> sameName(ParsedExercise parsed, String key, Set<MeasureType> savable) {
        boolean plain = parsed.modifiers().stream().map(SourceName::compact).allMatch(PLAIN_MODIFIERS::contains);
        String name = SourceName.compact(parsed.name());
        return Stream.concat(byKey.getOrDefault(key, List.of()).stream(),
                        plain ? byKey.getOrDefault(name, List.of()).stream() : Stream.<Exercise>empty())
                .filter(exercise -> savable.contains(exercise.getMeasureType()))
                .findFirst();
    }

    /**
     * 4번. {@code :} 앞 이름(띄어쓰기 뺀 것)이 어떤 계열 이름으로 끝나면 — 여럿이면 긴 계열 — 그 계열 안에서
     * 수식어 낱말이 이름에 가장 많이 들어 있는 종목. 비기면 {@code :} 앞 이름의 낱말 수, 흔한 종목, id 순.
     */
    private Optional<Exercise> similar(ParsedExercise parsed) {
        String name = SourceName.compact(parsed.name());
        Optional<String> group = groupsLongestFirst.stream()
                .filter(candidate -> name.endsWith(SourceName.compact(candidate)))
                .findFirst();
        if (group.isEmpty()) {
            return Optional.empty();
        }

        Set<MeasureType> likely = likely(parsed);
        List<String> modifierWords = parsed.modifiers().stream()
                .map(SourceName::compact)
                .map(word -> MODIFIER_WORDS.getOrDefault(word, word))
                .toList();
        List<String> nameWords = List.of(parsed.name().split(" "));
        return visible.stream()
                .filter(exercise -> group.get().equals(exercise.getGroupName()))
                .filter(exercise -> likely.contains(exercise.getMeasureType()))
                .min(Comparator.comparingInt((Exercise exercise) -> -hits(exercise, modifierWords))
                        .thenComparingInt(exercise -> -hits(exercise, nameWords))
                        .thenComparing(Exercise::getCommonRank, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Exercise::getId));
    }

    private static int hits(Exercise exercise, List<String> words) {
        String name = SourceName.compact(exercise.getNameKo());
        return (int) words.stream().map(SourceName::compact).filter(word -> !word.isEmpty() && name.contains(word))
                .count();
    }

    // ── 기록 방식

    /** 세트를 담을 수 있는 기록 방식 — 그 방식이 꼭 받는 값이 모든 세트에 있다. 남는 값은 저장할 때 버린다 */
    public static Set<MeasureType> savable(List<ParsedSet> sets) {
        Set<MeasureType> types = EnumSet.noneOf(MeasureType.class);
        if (sets.isEmpty()) {
            return types;
        }
        if (sets.stream().allMatch(set -> set.weightKg() != null && set.reps() != null)) {
            types.add(MeasureType.WEIGHT_REPS);
            types.add(MeasureType.WEIGHTED_BODYWEIGHT);
        }
        if (sets.stream().allMatch(set -> set.reps() != null)) {
            types.add(MeasureType.BODYWEIGHT_REPS);
        }
        if (sets.stream().allMatch(set -> set.durationSec() != null)) {
            types.add(MeasureType.TIME);
        }
        return types;
    }

    /** 짐작(4번)에 쓰는 기록 방식. kg가 적혔으면 맨몸 종목은 빼고 본다 — 이름이나 수식어에 "맨몸"이 있으면 넣는다 */
    static Set<MeasureType> likely(ParsedExercise parsed) {
        Set<MeasureType> types = savable(parsed.sets());
        boolean weighed = parsed.sets().stream().anyMatch(set -> set.weightKg() != null);
        if (weighed && !mentionsBodyweight(parsed)) {
            types.remove(MeasureType.BODYWEIGHT_REPS);
        }
        return types;
    }

    private static boolean mentionsBodyweight(ParsedExercise parsed) {
        return parsed.source().contains(BODYWEIGHT_WORD);
    }

    // ── 미등록 운동의 짐작

    /** 내 종목 만들기 창을 미리 채울 값. 모르면 null */
    public record Guess(Equipment equipment, MeasureType measureType) {
    }

    /**
     * 기구는 이름에 든 말로 — 케이블·머신·바벨·덤벨. 기록 방식은 세트로 — 시간이면 시간, kg가 있고 "맨몸"이 아니면
     * 무게 × 횟수, 아니면 횟수만. 기구 말이 없고 맨몸·시간이면 맨몸.
     */
    public static Guess guess(ParsedExercise parsed) {
        String source = SourceName.compact(parsed.source());
        List<ParsedSet> sets = parsed.sets();
        MeasureType measureType;
        if (sets.stream().allMatch(set -> set.durationSec() != null)) {
            measureType = MeasureType.TIME;
        } else if (sets.stream().allMatch(set -> set.weightKg() != null && set.reps() != null)
                && !mentionsBodyweight(parsed)) {
            measureType = MeasureType.WEIGHT_REPS;
        } else if (sets.stream().allMatch(set -> set.reps() != null)) {
            measureType = MeasureType.BODYWEIGHT_REPS;
        } else {
            measureType = null;
        }

        Equipment equipment;
        if (source.contains("케이블")) {
            equipment = Equipment.CABLE;
        } else if (source.contains("머신")) {
            equipment = Equipment.MACHINE;
        } else if (source.contains("바벨")) {
            equipment = Equipment.BARBELL;
        } else if (source.contains("덤벨")) {
            equipment = Equipment.DUMBBELL;
        } else if (measureType == MeasureType.BODYWEIGHT_REPS || measureType == MeasureType.TIME) {
            equipment = Equipment.BODYWEIGHT;
        } else {
            equipment = null;
        }
        return new Guess(equipment, measureType);
    }
}
