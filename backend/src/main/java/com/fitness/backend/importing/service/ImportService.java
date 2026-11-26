package com.fitness.backend.importing.service;

import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.importing.domain.BodyCalendarParser;
import com.fitness.backend.importing.domain.CuratedNames;
import com.fitness.backend.importing.domain.ExerciseMatcher;
import com.fitness.backend.importing.domain.ImportAlias;
import com.fitness.backend.importing.domain.MatchStatus;
import com.fitness.backend.importing.domain.ParsedLog;
import com.fitness.backend.importing.domain.ParsedLog.ParsedDay;
import com.fitness.backend.importing.domain.ParsedLog.ParsedExercise;
import com.fitness.backend.importing.domain.SetKind;
import com.fitness.backend.importing.domain.SourceName;
import com.fitness.backend.importing.repository.ImportAliasRepository;
import com.fitness.backend.importing.web.ImportDtos.ExerciseBrief;
import com.fitness.backend.importing.web.ImportDtos.ImportDay;
import com.fitness.backend.importing.web.ImportDtos.ImportExercise;
import com.fitness.backend.importing.web.ImportDtos.ImportRequest;
import com.fitness.backend.importing.web.ImportDtos.ImportResponse;
import com.fitness.backend.importing.web.ImportDtos.ImportSet;
import com.fitness.backend.importing.web.ImportDtos.ImportedDay;
import com.fitness.backend.importing.web.ImportDtos.PreviewDay;
import com.fitness.backend.importing.web.ImportDtos.PreviewExercise;
import com.fitness.backend.importing.web.ImportDtos.PreviewResponse;
import com.fitness.backend.workout.service.WorkoutService;
import com.fitness.backend.workout.web.WorkoutDtos.ImportDayResult;
import com.fitness.backend.workout.web.WorkoutDtos.ImportedSet;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 앱 기록 가져오기(「기록가져오기_설계서」). 첫 판은 바디캘린더 글만 읽는다.
 *
 * <p>미리보기는 읽고 맞추기만 하고 저장하지 않는다. 저장은 화면이 미리보기에서 정한 종목으로 보내고, 서버는 세트
 * 저장과 같은 규칙으로 다시 확인한다. 하루라도 막히면 아무것도 넣지 않는다(한 트랜잭션).
 */
@Service
@Transactional(readOnly = true)
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    static final int MAX_DAYS = 31;

    /** 세트 고정 번호(UUID v5)의 이름 공간. 바꾸면 전에 가져온 글을 다시 넣을 때 두 번 들어간다 */
    private static final UUID NAMESPACE = UUID.fromString("ad7cd7d8-0c81-4978-a060-086cc5f7bee4");
    private static final String SOURCE_APP = "bodycalendar";

    private final ExerciseRepository exerciseRepository;
    private final ImportAliasRepository aliasRepository;
    private final CuratedNames curatedNames;
    private final WorkoutService workoutService;
    private final Clock clock;

    public ImportService(ExerciseRepository exerciseRepository, ImportAliasRepository aliasRepository,
                         CuratedNames curatedNames, WorkoutService workoutService, Clock clock) {
        this.exerciseRepository = exerciseRepository;
        this.aliasRepository = aliasRepository;
        this.curatedNames = curatedNames;
        this.workoutService = workoutService;
        this.clock = clock;
    }

    /** 글을 날짜별로 읽고 우리 종목에 맞춘다. 미래 날짜는 뺀다(결정 12) */
    public PreviewResponse preview(Long userId, String text) {
        ParsedLog parsed = BodyCalendarParser.parse(text);
        logUnknownLines(parsed.unknownLines());

        LocalDate today = LocalDate.now(clock);
        List<ParsedDay> days = parsed.days().stream().filter(day -> !day.date().isAfter(today)).toList();
        if (days.size() > MAX_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "한 번에 31일까지 가져올 수 있어요.");
        }

        ExerciseMatcher matcher = matcherFor(userId);
        return new PreviewResponse(days.stream()
                .map(day -> new PreviewDay(day.date(), day.minutes(),
                        day.exercises().stream().map(exercise -> toPreview(exercise, matcher)).toList()))
                .toList());
    }

    /** 미리보기에서 정한 대로 넣는다. {@code remember}인 짝은 기억한다 */
    @Transactional
    public ImportResponse save(Long userId, ImportRequest request) {
        Set<LocalDate> dates = new HashSet<>();
        for (ImportDay day : request.days()) {
            if (!dates.add(day.date())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 날짜가 두 번 있어요.");
            }
        }

        List<ImportedDay> results = new ArrayList<>();
        for (ImportDay day : request.days()) {
            ImportDayResult result = workoutService.importDay(userId, day.date(), day.minutes(),
                    importedSets(userId, day));
            results.add(new ImportedDay(day.date(), result.sessionId(), result.added(), result.alreadyThere()));
        }

        // 세트가 모두 들어간 뒤에 — 위에서 종목이 보이는지, 기록 방식이 맞는지 이미 확인했다
        for (ImportDay day : request.days()) {
            for (ImportExercise exercise : day.exercises()) {
                if (exercise.rememberOrDefault()) {
                    aliasRepository.upsert(userId, SourceName.normalize(exercise.source()), exercise.exerciseId());
                }
            }
        }
        return new ImportResponse(results);
    }

    private ExerciseMatcher matcherFor(Long userId) {
        Map<String, Long> aliases = aliasRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(alias -> SourceName.key(alias.getSourceName()), ImportAlias::getExerciseId,
                        (first, second) -> first));
        return new ExerciseMatcher(exerciseRepository.findAllVisible(userId), aliases, curatedNames.byKey());
    }

    private static PreviewExercise toPreview(ParsedExercise parsed, ExerciseMatcher matcher) {
        ExerciseMatcher.Result match = matcher.match(parsed);
        return new PreviewExercise(parsed.source(), match.status(),
                match.exercise() == null ? null : ExerciseBrief.of(match.exercise()),
                match.status() == MatchStatus.UNREGISTERED ? ExerciseMatcher.guess(parsed) : null,
                List.copyOf(ExerciseMatcher.savable(parsed.sets())),
                parsed.sets().stream()
                        .map(set -> new ImportSet(set.kind(), set.weightKg(), set.reps(), set.durationSec(),
                                set.targetReps()))
                        .toList());
    }

    /**
     * 하루의 세트를 순서대로 펼친다. 세트마다 고정 번호를 붙인다(결정 7) — 사용자·날짜·원래 이름·그 이름이 그날
     * 몇 번째로 나왔는지·몇 번째 세트로 정해져, 같은 글을 다시 넣으면 같은 번호가 나와 세트 저장이 건너뛴다.
     * 다른 종목을 빼고 넣어도 번호가 바뀌지 않게 "몇 번째 종목" 대신 "같은 이름의 몇 번째"를 쓴다.
     */
    private static List<ImportedSet> importedSets(Long userId, ImportDay day) {
        Map<String, Integer> seen = new HashMap<>();
        List<ImportedSet> sets = new ArrayList<>();
        for (ImportExercise exercise : day.exercises()) {
            String key = SourceName.key(SourceName.normalize(exercise.source()));
            int occurrence = seen.merge(key, 1, Integer::sum) - 1;
            for (int i = 0; i < exercise.sets().size(); i++) {
                ImportSet set = exercise.sets().get(i);
                UUID clientSetId = uuidV5(String.join("\n", SOURCE_APP, String.valueOf(userId),
                        day.date().toString(), key, String.valueOf(occurrence), String.valueOf(i)));
                sets.add(new ImportedSet(clientSetId, exercise.exerciseId(), set.weightKg(), set.reps(),
                        set.durationSec(), set.kind().warmup(), set.kind() == SetKind.FAIL ? set.targetReps() : null));
            }
        }
        return sets;
    }

    /** RFC 4122 이름 기반 UUID(SHA-1, 버전 5). 자바 표준에는 버전 3(MD5)만 있다 */
    static UUID uuidV5(String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(ByteBuffer.allocate(16)
                    .putLong(NAMESPACE.getMostSignificantBits())
                    .putLong(NAMESPACE.getLeastSignificantBits())
                    .array());
            byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(hash, 0, 16);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1이 없다", e);
        }
    }

    /** 바디캘린더 형식이 바뀌면 알아채게. 글 전체는 남기지 않는다 */
    private static void logUnknownLines(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        List<String> examples = lines.stream().limit(3)
                .map(line -> line.length() > 60 ? line.substring(0, 60) + "…" : line)
                .toList();
        log.info("기록 가져오기: 읽지 못한 줄 {}개 — 예: {}", lines.size(), examples);
    }
}
