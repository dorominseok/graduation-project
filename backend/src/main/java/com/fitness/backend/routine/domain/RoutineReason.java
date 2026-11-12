package com.fitness.backend.routine.domain;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.analysis.domain.VolumeVerdict;
import com.fitness.backend.routine.domain.RoutineCheck.Imbalance;
import com.fitness.backend.routine.domain.SlotPlanner.Basis;
import com.fitness.backend.routine.domain.SlotPlanner.Lead;
import com.fitness.backend.routine.domain.SlotPlanner.Plan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "왜 오늘 이 부위인지" 한 줄. 루틴 화면 맨 위에 보인다.
 *
 * <p><b>규칙이 실제로 본 값만 적는다.</b> 재료는 {@link SlotPlanner#decide}가 돌려준 근거({@link Basis})뿐이다 —
 * 묶음을 정한 부위와 그 단계, 그 부위의 주당 세트·판정·마지막 운동일, 회복으로 쉬는 묶음. 문장을 따로 추론하지
 * 않으므로 루틴과 어긋날 수 없다. "가장 모자라서" 같은 비교급은 쓰지 않는다 — 코어처럼 묶음을 못 정하는 부위나
 * 불균형 때문에 뒤로 밀린 부위가 더 모자랄 수 있어, 맞는 날도 있고 틀리는 날도 있는 말이 된다.
 *
 * <p>LLM은 이 문장을 쓰지 않는다. LLM의 설명은 종목을 그렇게 고른 이유이고(숫자 금지), 판정과 수치는 여기서
 * 규칙이 쓴다. LLM에는 숫자를 뺀 요약({@link #brief})만 준다.
 */
public final class RoutineReason {

    private RoutineReason() {
    }

    /**
     * @param imbalances 불균형인 쌍마다 (적은 쪽, 많은 쪽). 적은 쪽 묶음을 먼저 한 날에 어느 쪽이 적은지 적는다
     */
    public static String of(Plan plan, List<Imbalance> imbalances, LocalDate today, RoutinePolicy policy) {
        Basis basis = plan.basis();
        String rest = rest(basis, today);
        if (plan.slots().isEmpty()) {
            return empty(basis, rest, policy);
        }
        if (basis.bundle().isEmpty()) {
            return coreOnly(basis, rest, policy);
        }

        return why(basis, rest, imbalances, today) + trimmed(basis, policy);
    }

    private static String why(Basis basis, String rest, List<Imbalance> imbalances, LocalDate today) {
        String bundle = basis.bundle().get().label();
        MuscleState lead = basis.lead();
        String name = lead.muscle().label();
        if (newcomer(basis)) {
            return "아직 기록이 없어서 큰 근육부터 시작해요. 오늘은 " + bundle + " 날이에요.";
        }
        // "주 3세트(부족)라서"로 쓰면 좁은 화면에서 괄호 뒤에서 줄이 갈린다
        String measured = name + subject(name) + " 주 " + number(lead.weeklySets()) + "세트로 "
                + (lead.verdict() == VolumeVerdict.INSUFFICIENT ? "부족해서" : lead.verdict().label() + "라서");
        Optional<Imbalance> imbalance = imbalanceOf(lead, imbalances);
        return switch (basis.stage()) {
            case LACKING_LESSER -> rest + imbalance.map(im -> smaller(im) + " 적고 ").orElse("")
                    + measured + " 오늘은 " + bundle + " 날이에요.";
            case LACKING, LACKING_BIGGER -> rest + measured + " 오늘은 " + bundle + " 날이에요.";
            case OPTIMAL_LESSER -> rest + imbalance.map(im -> smaller(im) + " 적어서 ").orElse("")
                    + "오늘은 " + bundle + " 날이에요.";
            case OPTIMAL, OPTIMAL_BIGGER -> rest + nothingLacking(basis, rest)
                    + "오늘은 " + rested(lead, today) + name + subject(name) + " 있는 " + bundle + " 날이에요.";
        };
    }

    /**
     * LLM에게 주는 숫자 없는 요약 — 그날 묶음과 그 묶음을 정한 이유. LLM의 설명에는 숫자가 금지라 수치를 빼고 준다.
     *
     * @return 묶음이 없으면(코어만 하거나 쉬는 날) 비어 있다
     */
    public static Optional<String> brief(Plan plan, List<Imbalance> imbalances) {
        Basis basis = plan.basis();
        if (basis.bundle().isEmpty() || plan.slots().isEmpty()) {
            return Optional.empty();
        }
        MuscleState lead = basis.lead();
        String name = lead.muscle().label();
        if (newcomer(basis)) {
            return Optional.of("기록이 없어 큰 근육부터");
        }
        Optional<Imbalance> imbalance = imbalanceOf(lead, imbalances);
        String verdict = name + subject(name) + " " + lead.verdict().label();
        return Optional.of(switch (basis.stage()) {
            case LACKING_LESSER -> imbalance.map(im -> smaller(im) + " 적고 ").orElse("") + verdict;
            case LACKING, LACKING_BIGGER -> verdict;
            case OPTIMAL_LESSER -> imbalance.map(im -> smaller(im) + " 적음").orElse(name + " 차례");
            case OPTIMAL, OPTIMAL_BIGGER -> (lacking(basis).isEmpty() ? "모자란 부위가 없고 " : "") + name + " 차례";
        });
    }

    // ── 담은 부위가 없거나 코어뿐인 날

    private static String empty(Basis basis, String rest, RoutinePolicy policy) {
        if (basis.available().isEmpty()) {
            List<String> resting = new ArrayList<>(basis.restingBundles().keySet().stream().map(MuscleBundle::label).toList());
            resting.add(MuscleGroup.CORE.label());
            String all = join(resting);
            return all + subject(all) + " 모두 회복 중이라 오늘은 담을 부위가 없어요.";
        }
        BigDecimal floor = policy.optimalThrough().subtract(BigDecimal.valueOf(policy.perMuscleMinSets()));
        boolean noRoom = basis.available().stream().allMatch(s -> s.weeklySets().compareTo(floor) > 0);
        if (noRoom) {
            BigDecimal least = basis.available().stream().map(MuscleState::weeklySets).min(BigDecimal::compareTo).orElseThrow();
            return (rest.isEmpty() ? "모든 부위가" : rest + "남은 부위는") + " 이미 주 " + number(least) + "세트 이상이라, 과다(주 "
                    + number(policy.optimalThrough()) + "세트 초과)가 되지 않게 오늘은 담지 않았어요.";
        }
        return rest + "오늘은 담을 부위가 없어요.";
    }

    private static String coreOnly(Basis basis, String rest, RoutinePolicy policy) {
        boolean bundlesAvailable = basis.available().stream().anyMatch(s -> MuscleBundle.of(s.muscle()).isPresent());
        if (!bundlesAvailable) {
            String all = join(basis.restingBundles().keySet().stream().map(MuscleBundle::label).toList());
            return all + subject(all) + " 모두 회복 중이라 오늘은 코어만 담았어요.";
        }
        // 묶음을 못 정했는데 쉬지 않는 묶음 부위가 있다 = 부족도 최적도 아닌, 과다인 부위뿐이다
        return rest + "남은 묶음 부위는 이미 과다(주 " + number(policy.optimalThrough()) + "세트 초과)라 오늘은 코어만 담았어요.";
    }

    /** " 가슴과 삼두는 주 20세트를 넘지 않게 줄여 담았어요." — 줄인 부위가 없으면 빈 문자열 */
    private static String trimmed(Basis basis, RoutinePolicy policy) {
        if (basis.trimmed().isEmpty()) {
            return "";
        }
        String names = join(basis.trimmed().stream().map(MuscleGroup::label).toList());
        return " " + names + topic(names) + " 주 " + number(policy.optimalThrough()) + "세트를 넘지 않게 줄여 담았어요.";
    }

    // ── 문장 조각

    /** "어제 한 밀기는 쉬고, " — 회복으로 쉬는 묶음이 없으면 빈 문자열 */
    private static String rest(Basis basis, LocalDate today) {
        if (basis.restingBundles().isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        basis.restingBundles().forEach((bundle, last) ->
                parts.add(when(ChronoUnit.DAYS.between(last, today)) + " 한 " + bundle.label()));
        String all = join(parts);
        return all + topic(all) + " 쉬고, ";
    }

    private static String when(long daysAgo) {
        return daysAgo == 0 ? "오늘" : daysAgo == 1 ? "어제" : daysAgo + "일 전에";
    }

    /** "5일 쉰 ". 마지막 운동일을 모르면 빈 문자열 */
    private static String rested(MuscleState lead, LocalDate today) {
        return lead.lastTrained() == null ? "" : ChronoUnit.DAYS.between(lead.lastTrained(), today) + "일 쉰 ";
    }

    /** 오늘 담을 수 있는 부위 중에 모자란 곳이 하나도 없을 때만 그렇게 말한다 */
    private static String nothingLacking(Basis basis, String rest) {
        if (!lacking(basis).isEmpty()) {
            return "";
        }
        return rest.isEmpty() ? "모자란 부위가 없어서 " : "남은 부위 중에 모자란 곳이 없어서 ";
    }

    private static List<MuscleState> lacking(Basis basis) {
        return basis.available().stream()
                .filter(s -> s.verdict() == VolumeVerdict.INSUFFICIENT || s.verdict() == VolumeVerdict.BELOW_RECOMMENDED)
                .toList();
    }

    /** 기록이 하나도 없는 사람 — 모든 조건이 같아 큰 근육 순서가 묶음을 정했다 */
    private static boolean newcomer(Basis basis) {
        return basis.restingBundles().isEmpty() && basis.available().stream()
                .allMatch(s -> s.lastTrained() == null && s.weeklySets().signum() == 0);
    }

    /** 묶음을 정한 부위가 적은 쪽인 불균형. "상체에 비해 하체가" */
    private static Optional<Imbalance> imbalanceOf(MuscleState lead, List<Imbalance> imbalances) {
        return imbalances.stream().filter(im -> im.lesser().components().contains(lead.muscle())).findFirst();
    }

    private static String smaller(Imbalance imbalance) {
        String lesser = imbalance.lesser().label();
        return imbalance.bigger().label() + "에 비해 " + lesser + subject(lesser);
    }

    /** 12.0 → "12", 1.8 → "1.8" */
    private static String number(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /** "밀기", "밀기와 당기기", "하체, 밀기와 당기기" */
    private static String join(List<String> words) {
        if (words.size() <= 1) {
            return words.isEmpty() ? "" : words.get(0);
        }
        String head = String.join(", ", words.subList(0, words.size() - 1));
        String last = words.get(words.size() - 1);
        return head + (batchim(head) ? "과 " : "와 ") + last;
    }

    private static String subject(String word) {
        return batchim(word) ? "이" : "가";
    }

    private static String topic(String word) {
        return batchim(word) ? "은" : "는";
    }

    /** 마지막 글자에 받침이 있는지. 한글이 아니면 없다고 본다 */
    private static boolean batchim(String word) {
        if (word.isEmpty()) {
            return false;
        }
        int code = word.charAt(word.length() - 1) - 0xAC00;
        return code >= 0 && code <= 11171 && code % 28 != 0;
    }
}
