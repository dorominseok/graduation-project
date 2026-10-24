package com.fitness.backend.routine.llm.experiment;

import com.fitness.backend.routine.llm.RoutinePrompt;
import com.fitness.backend.routine.llm.experiment.RoutineLlmExperiment.CaseStats;
import com.fitness.backend.routine.llm.experiment.RoutineLlmExperiment.Options;
import com.fitness.backend.routine.llm.experiment.RoutineLlmExperiment.Pool;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 실험 결과 표(summary.md). 「LLM 연동 실험기록」에 그대로 옮길 수 있게 마크다운으로 쓴다. */
final class Summary {

    private static final List<String> CODES = List.of("L1", "L2", "L3", "L4", "L5", "L6");

    private Summary() {
    }

    static String render(Options o, Map<String, CaseStats> stats, Pool pool, int rateLimited) {
        StringBuilder md = new StringBuilder();
        md.append("# 루틴 조합 LLM 실험 — ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n")
                .append("| 항목 | 값 |\n|---|---|\n")
                .append("| 모델 | `").append(o.model).append("` |\n")
                .append("| 프롬프트 | ").append(RoutinePrompt.VERSION).append(" |\n")
                .append("| temperature | ").append(o.temperature).append(" |\n")
                .append("| 재요청 | 최대 ").append(o.retries).append("회 |\n")
                .append("| 실행 | 케이스당 ").append(o.runs).append("회 |\n")
                .append("| 429 대기 | ").append(rateLimited).append("회 (실험에서만 한 번 기다렸다 다시 부름) |\n\n");

        md.append("## 케이스별 결과\n\n")
                .append("| 케이스 | 실행 | LLM 채택 | 1차 통과 | 설명 채택 | 평균 시도 | 폴백 검증/오류 | R1~R5 실패 "
                        + "| 규칙과 같음 | 처음 >2 | 순서 역전 | 슬롯 분리 | 조합 수 | 지연 p50/최대(ms) | 토큰 입력/출력 |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        Totals t = new Totals();
        for (CaseStats s : stats.values()) {
            md.append(row(s.c.id(), s.runs, s.llmAdopted, s.firstPass, s.llmComment, s.attempts, s.fallbackValidation,
                    s.fallbackError, s.rFail, s.sameAsRule, s.tooManyFirstTime, s.orderInverted, s.splitApart,
                    String.valueOf(s.combos.size()), s.latencies, s.promptTokens, s.outputTokens));
            t.add(s);
        }
        md.append(row("**합계**", t.runs, t.llm, t.first, t.comment, t.attempts, t.fbV, t.fbE, t.rFail, t.same,
                t.firstTime, t.inverted, t.apart, "—", t.latencies, t.prompt, t.output));

        md.append("\n- **LLM 채택**: 종목을 LLM이 고름(재요청 포함). **1차 통과**: 첫 답이 L1~L5 통과 — 핵심 지표 "
                        + "\"LLM 1차 통과율\"(설계서 11.1)\n")
                .append("- **설명 채택**: L6 통과로 LLM 설명을 씀. 아니면 규칙 문장\n")
                .append("- **처음 >2**: 해 본 종목이 있는 슬롯에서 처음 종목을 셋 이상 고름(피할 수 있었던 것만)\n")
                .append("- **규칙과 같음 / 처음 >2 / 순서 역전 / 슬롯 분리 / 조합 수**: LLM이 고른 실행만 센다. "
                        + "프롬프트 규칙이지만 검증(L)에는 없는 것 — 품질 참고\n")
                .append("- **R1~R5 실패**: 최종 루틴(폴백 포함)의 운동학적 기준(설계서 6.2)\n");

        md.append("\n## 검증 실패 유형 (모든 시도)\n\n| 케이스 | ")
                .append(String.join(" | ", CODES)).append(" | R 실패 내역 |\n|---|")
                .append("---|".repeat(CODES.size() + 1)).append('\n');
        for (CaseStats s : stats.values()) {
            md.append("| ").append(s.c.id()).append(" | ");
            for (String code : CODES) {
                md.append(s.failureCodes.getOrDefault(code, 0)).append(" | ");
            }
            md.append(s.rFailures.isEmpty() ? "—" : s.rFailures.toString()).append(" |\n");
        }

        md.append("\n## 케이스 입력과 규칙 선택\n");
        for (CaseStats s : stats.values()) {
            md.append("\n### ").append(s.c.id()).append(" — ").append(s.c.goal()).append("\n\n")
                    .append("- 슬롯: ").append(s.p.slots().stream().map(x -> x.muscle().label() + " " + x.sets())
                            .collect(Collectors.joining(", "))).append('\n')
                    .append("- 규칙 선택: ").append(s.p.ruleItems().stream()
                            .map(i -> pool.names.get(i.exerciseId()) + " ×" + i.sets())
                            .collect(Collectors.joining(" → "))).append('\n');
            List<String> samples = new ArrayList<>(new LinkedHashSet<>(s.comments)).subList(0,
                    Math.min(3, new LinkedHashSet<>(s.comments).size()));
            for (String c : samples) {
                md.append("- 설명: ").append(c).append('\n');
            }
            if (!s.errors.isEmpty()) {
                md.append("- 호출 오류: ").append(s.errors).append('\n');
            }
        }
        return md.toString();
    }

    private static String row(String id, int runs, int llm, int first, int comment, int attempts, int fbV, int fbE,
                              int rFail, int same, int firstTime, int inverted, int apart, String combos,
                              List<Long> latencies, List<Integer> prompt, List<Integer> output) {
        return "| " + id + " | " + runs + " | " + llm + " | " + first + " | " + comment + " | "
                + (runs == 0 ? "—" : String.format("%.2f", (double) attempts / runs)) + " | " + fbV + "/" + fbE + " | "
                + rFail + " | " + same + " | " + firstTime + " | " + inverted + " | " + apart + " | " + combos + " | "
                + p50(latencies) + "/" + latencies.stream().mapToLong(Long::longValue).max().orElse(0) + " | "
                + avg(prompt) + "/" + avg(output) + " |\n";
    }

    private static long p50(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private static long avg(Collection<Integer> values) {
        return Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private static final class Totals {
        int runs, llm, first, comment, attempts, fbV, fbE, rFail, same, firstTime, inverted, apart;
        final List<Long> latencies = new ArrayList<>();
        final List<Integer> prompt = new ArrayList<>();
        final List<Integer> output = new ArrayList<>();

        void add(CaseStats s) {
            runs += s.runs;
            llm += s.llmAdopted;
            first += s.firstPass;
            comment += s.llmComment;
            attempts += s.attempts;
            fbV += s.fallbackValidation;
            fbE += s.fallbackError;
            rFail += s.rFail;
            same += s.sameAsRule;
            firstTime += s.tooManyFirstTime;
            inverted += s.orderInverted;
            apart += s.splitApart;
            latencies.addAll(s.latencies);
            prompt.addAll(s.promptTokens);
            output.addAll(s.outputTokens);
        }
    }
}
