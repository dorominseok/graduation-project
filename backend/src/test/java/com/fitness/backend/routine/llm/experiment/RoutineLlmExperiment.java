package com.fitness.backend.routine.llm.experiment;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.exercise.domain.DeltRegion;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.routine.domain.CandidatePicker;
import com.fitness.backend.routine.domain.ExerciseOption;
import com.fitness.backend.routine.domain.PlannedItem;
import com.fitness.backend.routine.domain.RoutineCheck;
import com.fitness.backend.routine.domain.RoutinePolicy;
import com.fitness.backend.routine.domain.RoutineSlot;
import com.fitness.backend.routine.domain.RuleComposer;
import com.fitness.backend.routine.domain.SlotPlanner;
import com.fitness.backend.routine.llm.GeminiClient;
import com.fitness.backend.routine.llm.LlmCallException;
import com.fitness.backend.routine.llm.LlmClient;
import com.fitness.backend.routine.llm.LlmRoutineComposer;
import com.fitness.backend.routine.llm.LlmRoutineComposer.Attempt;
import com.fitness.backend.routine.llm.LlmRoutineComposer.Composition;
import com.fitness.backend.routine.llm.LlmRoutineRequest;
import com.fitness.backend.routine.llm.RoutinePrompt;
import com.fitness.backend.routine.llm.experiment.ExperimentCases.Case;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/**
 * 루틴 조합 LLM 오프라인 실험. 「루틴생성_로직_설계서」 11.5.
 *
 * <pre>
 *   ./gradlew llmExperiment                                  # 8케이스 × 10회
 *   ./gradlew llmExperiment --args="--runs 3 --cases 01,02"
 *   ./gradlew llmExperiment --args="--dry-run"               # 호출 없이 입력만
 *   옵션: --model gemini-3.5-flash-lite  --temperature 0.4  --retries 2  --interval-ms 4500
 * </pre>
 *
 * <p>운영과 같은 부품(LlmRoutineComposer·GeminiClient·RoutinePrompt·검증기)을 그대로 쓴다. 실험한 코드가
 * 운영 코드다. 결과는 {@code scripts/llm-experiment/runs/routine_*}(git 미추적)에 남긴다.
 *
 * <p>키는 리포 루트 {@code .env}의 {@code GEMINI_API_KEY}에서 읽고 어디에도 출력하지 않는다.
 * 실제 Gemini 무료 쿼터를 쓰므로 CI·{@code test}에서는 돌지 않는다(LLM 실험기록 6.2).
 */
public final class RoutineLlmExperiment {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final RoutinePolicy POLICY = RoutinePolicy.DEFAULT;
    private static final int CANDIDATES_PER_MUSCLE = 5;
    private static final int HISTORY_MAX = 3;

    private RoutineLlmExperiment() {
    }

    public static void main(String[] args) throws IOException {
        Options options = Options.parse(args);
        Path root = repoRoot();
        Pool pool = Pool.load(root.resolve("backend/src/main/resources/data/exercises.csv"));
        LocalDate today = LocalDate.now();
        List<Case> cases = ExperimentCases.all(today).stream()
                .filter(c -> options.cases.isEmpty() || options.cases.stream().anyMatch(p -> c.id().startsWith(p)))
                .toList();

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"));
        Path out = root.resolve("scripts/llm-experiment/runs/routine_" + stamp + "_" + options.model + "_"
                + RoutinePrompt.VERSION + (options.dryRun ? "_dry" : ""));
        Files.createDirectories(out);

        PacedClient client = options.dryRun ? null
                : new PacedClient(new GeminiClient(JSON, options.model, apiKey(root), Duration.ofSeconds(8), 1024),
                        options.intervalMs);
        LlmRoutineComposer composer = options.dryRun ? null
                : new LlmRoutineComposer(client, JSON, options.retries, options.temperature);

        StringBuilder promptLog = new StringBuilder("# 프롬프트 " + RoutinePrompt.VERSION + "\n\n" + RoutinePrompt.SYSTEM);
        List<String> raw = new ArrayList<>();
        Map<String, CaseStats> stats = new LinkedHashMap<>();

        for (Case c : cases) {
            Prepared p = prepare(c, pool, today);
            promptLog.append("\n\n# ").append(c.id()).append(" — ").append(c.goal()).append("\n")
                    .append(RoutinePrompt.of(p.request, List.of(), options.temperature, JSON).user());
            System.out.printf("%n[%s] %s — 슬롯 %s%n", c.id(), c.goal(), p.slots.stream()
                    .map(s -> s.muscle().name() + ":" + s.sets()).collect(Collectors.joining(" ")));
            if (options.dryRun) {
                continue;
            }
            CaseStats cs = new CaseStats(c, p);
            stats.put(c.id(), cs);
            for (int run = 1; run <= options.runs; run++) {
                Composition result = composer.compose(p.slots, p.candidates, pool.names);
                Map<String, Object> record = cs.add(result, pool);
                record.put("case", c.id());
                record.put("run", run);
                raw.add(JSON.writeValueAsString(record));
                System.out.printf("  #%02d %s/%s 시도 %d %s %s%n", run, result.generatedBy(), result.commentBy(),
                        result.llmAttempts(), record.get("failureCodes"), result.comment());
            }
        }

        Files.writeString(out.resolve("prompt.txt"), promptLog, StandardCharsets.UTF_8);
        if (!options.dryRun) {
            Files.write(out.resolve("raw.jsonl"), raw, StandardCharsets.UTF_8);
            Files.writeString(out.resolve("summary.md"), Summary.render(options, stats, pool, client.rateLimited),
                    StandardCharsets.UTF_8);
        }
        System.out.println("\n결과: " + root.relativize(out));
    }

    // ── 케이스 → 운영과 같은 길로 슬롯·후보

    record Prepared(List<RoutineSlot> slots, Map<MuscleGroup, List<ExerciseOption>> candidates,
                    LlmRoutineRequest request, List<PlannedItem> ruleItems, Map<Long, Boolean> firstTime) {
    }

    static Prepared prepare(Case c, Pool pool, LocalDate today) {
        List<RoutineSlot> slots = SlotPlanner.plan(c.states(), c.daysPerWeek(), today, POLICY);
        Map<MuscleGroup, List<ExerciseOption>> candidates = new EnumMap<>(MuscleGroup.class);
        Map<Long, Boolean> firstTime = new HashMap<>();
        for (RoutineSlot slot : slots) {
            List<ExerciseOption> options = pool.byMuscle.getOrDefault(slot.muscle(), List.of()).stream()
                    .map(e -> {
                        Integer days = c.recent().get(e.name);
                        return new ExerciseOption(e.id, e.group, e.equipment, c.favorites().contains(e.name),
                                days == null ? 0 : days, days == null, e.starterRank);
                    })
                    .toList();
            List<ExerciseOption> picked = CandidatePicker.pick(options, CANDIDATES_PER_MUSCLE, HISTORY_MAX);
            picked.forEach(o -> firstTime.put(o.id(), o.firstTime()));
            candidates.put(slot.muscle(), picked);
        }
        for (String name : c.recent().keySet()) {
            pool.require(name);
        }
        c.favorites().forEach(pool::require);
        return new Prepared(slots, candidates, LlmRoutineRequest.of(slots, candidates, pool.names),
                RuleComposer.compose(slots, candidates), firstTime);
    }

    // ── 케이스별 집계

    static final class CaseStats {
        final Case c;
        final Prepared p;
        int runs;
        int llmAdopted;
        int firstPass;
        int llmComment;
        int fallbackValidation;
        int fallbackError;
        int attempts;
        int rFail;
        int sameAsRule;
        int tooManyFirstTime;
        int orderInverted;
        int splitApart;
        final Map<String, Integer> failureCodes = new TreeMap<>();
        final Map<String, Integer> rFailures = new TreeMap<>();
        final List<Long> latencies = new ArrayList<>();
        final List<Integer> promptTokens = new ArrayList<>();
        final List<Integer> outputTokens = new ArrayList<>();
        final Set<String> combos = new HashSet<>();
        final List<String> comments = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        CaseStats(Case c, Prepared p) {
            this.c = c;
            this.p = p;
        }

        Map<String, Object> add(Composition r, Pool pool) {
            runs++;
            attempts += r.llmAttempts();
            boolean llm = r.generatedBy().name().equals("LLM");
            if (llm) {
                llmAdopted++;
            }
            if (r.commentBy().name().equals("LLM")) {
                llmComment++;
                comments.add(r.comment());
            }
            if (r.fallbackReason() != null) {
                switch (r.fallbackReason()) {
                    case VALIDATION_FAILED -> fallbackValidation++;
                    case LLM_ERROR -> fallbackError++;
                    default -> {
                    }
                }
            }
            List<Attempt> tries = r.attempts();
            if (!tries.isEmpty() && tries.get(0).error() == null
                    && tries.get(0).failures().stream().allMatch(f -> f.code().equals("L6"))) {
                firstPass++;
            }
            Set<String> codes = new TreeSet<>();
            for (Attempt a : tries) {
                if (a.error() != null) {
                    errors.add(a.error());
                    continue;
                }
                latencies.add(a.latencyMs());
                promptTokens.add(a.promptTokens());
                outputTokens.add(a.outputTokens());
                a.failures().forEach(f -> {
                    failureCodes.merge(f.code(), 1, Integer::sum);
                    codes.add(f.code());
                });
            }

            List<String> rf = RoutineCheck.check(p.slots, r.items(), c.imbalances(), candidateIds(p.candidates), POLICY);
            if (!rf.isEmpty()) {
                rFail++;
                rf.forEach(code -> rFailures.merge(code, 1, Integer::sum));
            }
            if (llm) {
                if (r.items().equals(p.ruleItems)) {
                    sameAsRule++;
                }
                if (avoidableFirstTime(r.items()) > 2) {
                    tooManyFirstTime++;
                }
                if (inverted(r.items())) {
                    orderInverted++;
                }
                if (splitApart(r.items())) {
                    splitApart++;
                }
                combos.add(r.items().stream().map(i -> i.exerciseId() + "x" + i.sets())
                        .collect(Collectors.joining(",")));
            }

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("generatedBy", r.generatedBy());
            record.put("commentBy", r.commentBy());
            record.put("fallbackReason", r.fallbackReason());
            record.put("failureCodes", codes);
            record.put("finalCheckFailures", rf);
            record.put("items", r.items().stream().map(i -> i.muscle() + " " + pool.names.get(i.exerciseId())
                    + " ×" + i.sets()).toList());
            record.put("comment", r.comment());
            record.put("attempts", tries);
            return record;
        }

        /**
         * 해 본 종목이 있는 슬롯에서 고른 처음 종목 수. 해 본 종목이 없는 슬롯(기록 없는 사람)은 처음 종목을
         * 고를 수밖에 없어 세지 않는다 — 프롬프트 r2의 규칙과 같은 기준
         */
        private long avoidableFirstTime(List<PlannedItem> items) {
            return items.stream()
                    .filter(i -> p.firstTime.getOrDefault(i.exerciseId(), true))
                    .filter(i -> p.candidates.get(i.muscle()).stream().anyMatch(o -> !o.firstTime()))
                    .count();
        }

        /** 큰 근육 → 작은 근육 → 코어 순서를 거스른 곳이 있다 */
        private static boolean inverted(List<PlannedItem> items) {
            for (int i = 1; i < items.size(); i++) {
                if (rank(items.get(i).muscle()) < rank(items.get(i - 1).muscle())) {
                    return true;
                }
            }
            return false;
        }

        /** 한 슬롯의 두 종목이 떨어져 있다 */
        private static boolean splitApart(List<PlannedItem> items) {
            Map<MuscleGroup, Integer> last = new EnumMap<>(MuscleGroup.class);
            for (int i = 0; i < items.size(); i++) {
                Integer prev = last.put(items.get(i).muscle(), i);
                if (prev != null && prev != i - 1) {
                    return true;
                }
            }
            return false;
        }

        private static int rank(MuscleGroup m) {
            return switch (m) {
                case CHEST, BACK, QUADS, POSTERIOR -> 0;
                case CORE -> 2;
                default -> 1;
            };
        }
    }

    static Map<MuscleGroup, Set<Long>> candidateIds(Map<MuscleGroup, List<ExerciseOption>> candidates) {
        Map<MuscleGroup, Set<Long>> result = new EnumMap<>(MuscleGroup.class);
        candidates.forEach((m, list) -> result.put(m, list.stream().map(ExerciseOption::id).collect(Collectors.toSet())));
        return result;
    }

    // ── 종목 시드 (CSV 순서 = 새 DB에 들어가는 id 순서)

    record Exercise(long id, String name, MuscleGroup muscle, Equipment equipment, String group, Integer starterRank) {
    }

    static final class Pool {
        final Map<Long, String> names = new HashMap<>();
        final Map<MuscleGroup, List<Exercise>> byMuscle = new EnumMap<>(MuscleGroup.class);
        private final Set<String> all = new HashSet<>();

        static Pool load(Path csv) throws IOException {
            Pool pool = new Pool();
            List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            List<String> header = List.of(lines.get(0).split(",", -1));
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                String[] f = lines.get(i).split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int k = 0; k < header.size(); k++) {
                    row.put(header.get(k), f[k]);
                }
                long id = i;
                String name = row.get("name_ko");
                pool.names.put(id, name);
                pool.all.add(name);
                DeltRegion delt = row.get("delt_region").isEmpty() ? null : DeltRegion.valueOf(row.get("delt_region"));
                MuscleGroup.of(row.get("primary_muscle"), delt).filter(MuscleGroup::judged).ifPresent(m ->
                        pool.byMuscle.computeIfAbsent(m, x -> new ArrayList<>()).add(new Exercise(id, name, m,
                                Equipment.valueOf(row.get("equipment")),
                                row.get("group_name").isEmpty() ? null : row.get("group_name"),
                                row.get("starter_rank").isEmpty() ? null : Integer.valueOf(row.get("starter_rank")))));
            }
            return pool;
        }

        void require(String name) {
            if (!all.contains(name)) {
                throw new IllegalStateException("시드에 없는 종목: " + name);
            }
        }
    }

    // ── 실행 환경

    /** 무료 티어 분당 한도를 넘지 않게 호출 사이를 띄운다. 429면 한 번 기다렸다 다시 — 실험에서만 */
    static final class PacedClient implements LlmClient {
        private final LlmClient inner;
        private final long intervalMs;
        private long lastCall;
        int rateLimited;

        PacedClient(LlmClient inner, long intervalMs) {
            this.inner = inner;
            this.intervalMs = intervalMs;
        }

        @Override
        public Response generate(Prompt prompt) {
            sleep(lastCall + intervalMs - System.currentTimeMillis());
            try {
                return inner.generate(prompt);
            } catch (LlmCallException e) {
                if (e.kind() != LlmCallException.Kind.HTTP || !e.getMessage().contains("429")) {
                    throw e;
                }
                rateLimited++;
                System.out.println("    · HTTP 429 → 40초 쉬고 한 번 더");
                sleep(40_000);
                return inner.generate(prompt);
            } finally {
                lastCall = System.currentTimeMillis();
            }
        }

        private static void sleep(long ms) {
            if (ms <= 0) {
                return;
            }
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static final class Options {
        String model = "gemini-3.5-flash-lite";
        double temperature = 0.4;
        int retries = 2;
        int runs = 10;
        long intervalMs = 4500;
        boolean dryRun;
        List<String> cases = List.of();

        static Options parse(String[] args) {
            Options o = new Options();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--model" -> o.model = args[++i];
                    case "--temperature" -> o.temperature = Double.parseDouble(args[++i]);
                    case "--retries" -> o.retries = Integer.parseInt(args[++i]);
                    case "--runs" -> o.runs = Integer.parseInt(args[++i]);
                    case "--interval-ms" -> o.intervalMs = Long.parseLong(args[++i]);
                    case "--cases" -> o.cases = List.of(args[++i].split(","));
                    case "--dry-run" -> o.dryRun = true;
                    default -> throw new IllegalArgumentException("모르는 옵션: " + args[i]);
                }
            }
            return o;
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("scripts/llm-experiment"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("리포 루트(scripts/llm-experiment)를 찾지 못했다");
        }
        return dir;
    }

    /** {@code GEMINI_API_KEY}를 환경변수 → 리포 루트 .env 순으로 찾는다. 값은 출력하지 않는다 */
    private static String apiKey(Path root) throws IOException {
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.strip();
        }
        Path file = root.resolve(".env");
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = line.strip().replace("﻿", "");
                if (t.startsWith("GEMINI_API_KEY=")) {
                    String v = t.substring("GEMINI_API_KEY=".length()).strip().replaceAll("^[\"']|[\"']$", "");
                    if (!v.isEmpty()) {
                        return v;
                    }
                }
            }
        }
        throw new IllegalStateException(".env에 GEMINI_API_KEY가 없다");
    }
}
