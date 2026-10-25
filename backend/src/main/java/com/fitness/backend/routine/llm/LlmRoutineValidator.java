package com.fitness.backend.routine.llm;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.routine.llm.LlmRoutineRequest.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * LLM 출력 검증 L1~L6.
 *
 * <p>L1~L5는 종목 선택이고 하나라도 걸리면 재요청한다. L6은 설명뿐이라 걸려도 선택은 받아들이고
 * 설명만 규칙 문장으로 바꾼다 — 멀쩡한 선택을 문장 때문에 버리지 않는다.
 */
public final class LlmRoutineValidator {

    static final int MAX_PER_SLOT = 2;
    static final int COMMENT_MAX_LENGTH = 120;

    /** 아라비아 숫자. 설명에 숫자를 쓰면 화면의 근거(규칙이 쓴 숫자)와 어긋날 수 있다 */
    private static final Pattern DIGIT = Pattern.compile("\\d");
    /** 말로 쓴 수량 — "두 세트", "세 번 반복"은 숫자를 우회한 것과 같다. "한 번에"는 막지 않는다 */
    private static final Pattern COUNTED = Pattern.compile(
            "(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*(세트|회|kg|킬로|초|분|배|퍼센트)");
    /** 처방형 종결 어미. 분석 해설 실험에서 다듬은 사전을 그대로 쓴다 */
    private static final Pattern PRESCRIPTIVE = Pattern.compile(
            "(하세요|하십시오|하시기 바랍|해야\\s*(합니다|한다|해요|겠)|필요합니다|필요하다|"
                    + "권장(합니다|한다|됩니다|해요|드립니다)|추천(합니다|해요|드립니다|드려요)|"
                    + "보완하(세요|셔야|시고|시길|는 것이)|늘리(세요|셔야|시길|는 것이|는 것을)|"
                    + "줄이(세요|셔야|시길)|추가하(세요|셔야)|높이(세요|셔야)|올리(세요|셔야)|채우(세요|셔야)|"
                    + "하는 것이 좋(습니다|겠|아요)|바랍니다)");
    /** 의학적 단정 — 진단·부상 위험은 말하지 않는다 */
    private static final Pattern MEDICAL = Pattern.compile("부상|통증|재활|치료|진단");
    /** 부위를 가리키는 말. 판정 부위 이름(라벨)의 일부이거나 이 묶음 이름이면 된다 */
    private static final Set<String> BODY_WORDS = Set.of("하체", "상체", "팔", "다리", "허벅지", "엉덩이", "전면", "측면");

    private LlmRoutineValidator() {
    }

    public record Failure(String code, String detail) {
    }

    public record Pick(MuscleGroup slot, long exerciseId) {
    }

    /**
     * @param picks   L1을 통과하면 응답 순서대로, 아니면 비어 있다
     * @param comment L1을 통과하면 설명, 아니면 null
     */
    public record Result(List<Pick> picks, String comment, List<Failure> failures) {

        /** 종목 선택이 쓸 만하다 — L1~L5 통과 */
        public boolean selectionOk() {
            return failures.stream().allMatch(f -> f.code().equals("L6"));
        }

        public boolean commentOk() {
            return failures.stream().noneMatch(f -> f.code().equals("L6"));
        }

        public Set<String> codes() {
            Set<String> codes = new TreeSet<>();
            failures.forEach(f -> codes.add(f.code()));
            return codes;
        }
    }

    public static Result validate(LlmRoutineRequest request, String text, ObjectMapper json) {
        Optional<Parsed> parsed = parse(text, json);
        if (parsed.isEmpty()) {
            return new Result(List.of(), null,
                    List.of(new Failure("L1", "응답이 {items:[{slot, exerciseId}], comment} 형식이 아니었다")));
        }
        List<Pick> picks = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        Map<MuscleGroup, Integer> perSlot = new EnumMap<>(MuscleGroup.class);
        Set<Long> seen = new HashSet<>();

        for (RawPick raw : parsed.get().items()) {
            Optional<Slot> slot = slotOf(request, raw.slot());
            Long id = toLong(raw.exerciseId());
            if (slot.isEmpty()) {
                failures.add(new Failure("L2", "slot \"" + raw.slot() + "\"은 이번 입력에 없다"));
                continue;
            }
            boolean candidate = id != null && slot.get().candidates().stream().anyMatch(c -> c.exerciseId() == id);
            if (!candidate) {
                failures.add(new Failure("L2", slot.get().muscle().name() + " slot에 그 slot의 후보가 아닌 exerciseId \""
                        + raw.exerciseId() + "\"를 넣었다"));
                continue;
            }
            if (!seen.add(id)) {
                failures.add(new Failure("L4", "exerciseId \"" + id + "\"를 두 번 넣었다"));
                continue;
            }
            perSlot.merge(slot.get().muscle(), 1, Integer::sum);
            picks.add(new Pick(slot.get().muscle(), id));
        }

        for (Slot slot : request.slots()) {
            int count = perSlot.getOrDefault(slot.muscle(), 0);
            if (count == 0 || count > MAX_PER_SLOT) {
                failures.add(new Failure("L3", slot.muscle().name() + " slot의 종목이 " + count
                        + "개다. 모든 slot에 1~2개를 넣는다"));
            } else if (count == 2 && !slot.splittable()) {
                failures.add(new Failure("L5", slot.muscle().name() + " slot은 split이 false라 종목을 1개만 넣는다"));
            }
        }

        commentProblem(parsed.get().comment())
                .or(() -> wrongBodyWord(parsed.get().comment(), request, picks))
                .ifPresent(p -> failures.add(new Failure("L6", p)));
        return new Result(List.copyOf(picks), parsed.get().comment(), List.copyOf(failures));
    }

    /** 설명의 문제 하나. 없으면 비어 있다 */
    static Optional<String> commentProblem(String comment) {
        if (comment == null || comment.isBlank()) {
            return Optional.of("comment가 비었다");
        }
        if (comment.length() > COMMENT_MAX_LENGTH) {
            return Optional.of("comment가 " + COMMENT_MAX_LENGTH + "자를 넘었다");
        }
        if (DIGIT.matcher(comment).find() || COUNTED.matcher(comment).find()) {
            return Optional.of("comment에 숫자나 수량(세트·횟수·무게 등)을 썼다");
        }
        if (PRESCRIPTIVE.matcher(comment).find()) {
            return Optional.of("comment에 지시·권유 표현을 썼다");
        }
        if (MEDICAL.matcher(comment).find()) {
            return Optional.of("comment에 부상·통증 같은 의학적 표현을 썼다");
        }
        return Optional.empty();
    }

    /**
     * "○○의 [고른 종목]"에서 ○○가 부위 이름이 아니다. 운영 모델이 기록 없는 첫날 설명에서 "등의 랫풀다운"을
     * "정의 랫풀다운"으로 쓰는 버릇이 있다(오프라인 실험 r2·실호출에서 반복).
     * 새 사용자가 처음 보는 문장이라 이 꼴만 좁게 잡는다. 설명은 종목 이름을 줄여 쓰므로("랫풀다운: 와이드 그립" →
     * "랫풀다운") 콜론 앞과 마지막 낱말도 본다. 지금까지의 실제 설명 191개에 대 보면 이 실수 2건만 잡는다
     */
    static Optional<String> wrongBodyWord(String comment, LlmRoutineRequest request, List<Pick> picks) {
        if (comment == null) {
            return Optional.empty();
        }
        Set<String> chosen = new HashSet<>();
        for (Pick pick : picks) {
            request.slot(pick.slot()).flatMap(s -> s.candidates().stream()
                    .filter(c -> c.exerciseId() == pick.exerciseId()).findFirst())
                    .ifPresent(c -> chosen.add(c.name()));
        }
        for (String name : chosen) {
            String base = name.split(":")[0].strip();
            String[] words = base.split("\\s+");
            for (String shown : new LinkedHashSet<>(List.of(name, base, words[words.length - 1]))) {
                Matcher m = Pattern.compile("(\\S+)의\\s+" + Pattern.quote(shown)).matcher(comment);
                while (m.find()) {
                    String word = m.group(1).replaceAll("^[,·(]+", "");
                    boolean bodyPart = BODY_WORDS.contains(word) || Arrays.stream(MuscleGroup.values())
                            .anyMatch(g -> g.label().contains(word));
                    if (!bodyPart) {
                        return Optional.of("comment에서 \"" + word + "의 " + shown + "\"처럼 부위 이름을 잘못 썼다");
                    }
                }
            }
        }
        return Optional.empty();
    }

    private record RawPick(String slot, String exerciseId) {
    }

    private record Parsed(List<RawPick> items, String comment) {
    }

    private static Optional<Parsed> parse(String text, ObjectMapper json) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        JsonNode items = root.path("items");
        if (!items.isArray() || !root.path("comment").isString()) {
            return Optional.empty();
        }
        List<RawPick> picks = new ArrayList<>();
        for (JsonNode item : items) {
            // 스키마가 문자열을 강제하지만, 숫자로 와도 같은 값이면 받는다
            String slot = item.path("slot").asString(null);
            String id = item.path("exerciseId").asString(null);
            if (slot == null || id == null) {
                return Optional.empty();
            }
            picks.add(new RawPick(slot, id));
        }
        return Optional.of(new Parsed(picks, root.path("comment").asString()));
    }

    private static Optional<Slot> slotOf(LlmRoutineRequest request, String key) {
        return request.slots().stream().filter(s -> s.muscle().name().equals(key)).findFirst();
    }

    private static Long toLong(String value) {
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
