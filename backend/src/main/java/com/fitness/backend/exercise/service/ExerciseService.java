package com.fitness.backend.exercise.service;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.exercise.domain.BodyPart;
import com.fitness.backend.exercise.domain.BrowseCategory;
import com.fitness.backend.exercise.domain.CustomTarget;
import com.fitness.backend.exercise.domain.DeltRegion;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.exercise.domain.UserFavoriteExercise;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.exercise.repository.UserFavoriteExerciseRepository;
import com.fitness.backend.exercise.web.ExerciseDtos.CategoryCount;
import com.fitness.backend.exercise.web.ExerciseDtos.CustomExerciseRequest;
import com.fitness.backend.exercise.web.ExerciseDtos.ExerciseResponse;
import com.fitness.backend.exercise.web.ExerciseDtos.GroupCount;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 조회·즐겨찾기, 그리고 내 종목 만들기·고치기·지우기(「내종목_설계서」).
 *
 * <p><b>보이는 범위</b>는 어디서나 같다 — 기본 종목과, 요청한 사람이 만든 숨기지 않은 내 종목. 남의 내 종목은
 * 목록에 안 나오고 id로 직접 와도 없는 종목(404)이다. 있는지조차 드러내지 않는다(명세 1.3과 같은 원칙).
 */
@Service
@Transactional(readOnly = true)
public class ExerciseService {

    /** 한 계정이 만들 수 있는 내 종목 수(숨긴 것 포함). 넘을 일이 없을 만큼 — 끝없이 쌓이지만 않게 */
    static final int CUSTOM_LIMIT = 100;

    private final ExerciseRepository exerciseRepository;
    private final UserFavoriteExerciseRepository favoriteRepository;
    private final Clock clock;

    public ExerciseService(ExerciseRepository exerciseRepository,
                           UserFavoriteExerciseRepository favoriteRepository,
                           Clock clock) {
        this.exerciseRepository = exerciseRepository;
        this.favoriteRepository = favoriteRepository;
        this.clock = clock;
    }

    /**
     * 종목 검색.
     *
     * @param userId        인증된 사용자. 비인증이면 {@code null}이고 {@code isFavorite}를 담지 않으며 기본 종목만 본다
     * @param favoriteOnly  즐겨찾기만. 비인증 요청에서는 무시한다 — 기준이 될 사용자가 없다
     * @param ungroupedMine 계열 없이 만든 내 종목만 — 부위 화면의 "내 종목" 칸
     */
    public Page<ExerciseResponse> search(Long userId, String q, BodyPart bodyPart,
                                         Equipment equipment, MeasureType measureType,
                                         BrowseCategory category, MuscleGroup muscleGroup,
                                         String groupName, boolean favoriteOnly, boolean ungroupedMine,
                                         Pageable pageable) {
        String namePattern = namePattern(q);
        // 분류도 판정 부위도 없으면 in 절을 건너뛴다. 빈 컬렉션을 넘기면 아무것도 안 나온다.
        boolean allMuscles = category == null && muscleGroup == null;
        Collection<String> muscles = allMuscles ? List.of("") : musclesOf(category, muscleGroup);
        // 어깨 앞뒤는 분류(어깨 전·측면·후면)와 판정 부위(DELT_FRONT·REAR) 양쪽에서 올 수 있다.
        // 서로 다른 쪽을 가리키면 겹치는 종목이 없다
        DeltRegion fromCategory = category == null ? null : category.deltRegion();
        DeltRegion fromGroup = muscleGroup == null ? null : muscleGroup.deltRegion();
        if (fromCategory != null && fromGroup != null && fromCategory != fromGroup) {
            return Page.empty(pageable);
        }
        DeltRegion deltRegion = fromCategory != null ? fromCategory : fromGroup;

        Page<Exercise> page = favoriteOnly && userId != null
                ? exerciseRepository.searchFavorites(userId, namePattern, bodyPart, equipment, measureType,
                        allMuscles, muscles, groupName, deltRegion, pageable)
                : exerciseRepository.search(namePattern, bodyPart, equipment, measureType,
                        allMuscles, muscles, groupName, deltRegion, viewer(userId), ungroupedMine, pageable);

        Set<Long> favorites = favoriteIds(userId, page.getContent());
        return page.map(e -> toResponse(e, userId, favorites));
    }

    /**
     * 탐색 분류와 판정 부위를 같이 주면 둘 다 만족하는 근육만 남긴다.
     *
     * <p>둘은 다른 축이다. 판정 부위 "뒤허벅지·둔근"은 탐색 분류의 하체와
     * 엉덩이에 걸쳐 있어 분류 하나로는 못 고른다 — 홈의 약점에서 종목으로 바로
     * 가려면 판정 부위로 조회할 수 있어야 한다. 겹치는 근육이 없으면 아무것도
     * 안 나오도록 빈 문자열 하나를 넘긴다.
     */
    private static Collection<String> musclesOf(BrowseCategory category, MuscleGroup muscleGroup) {
        if (category == null) {
            return muscleGroup.primaryMuscles();
        }
        if (muscleGroup == null) {
            return category.primaryMuscles();
        }
        List<String> both = category.primaryMuscles().stream()
                .filter(muscleGroup.primaryMuscles()::contains)
                .toList();
        return both.isEmpty() ? List.of("") : both;
    }

    /**
     * 부위 그리드. 13종 분류와 각 종목 수를 준다. 내 종목은 만든 사람에게만 센다.
     *
     * <p>종목이 없는 분류도 담는다 — 그리드에서 칸이 사라지면 자리가 밀려
     * 사용자가 위치로 기억한 것을 다시 찾아야 한다.
     */
    public List<CategoryCount> categories(Long userId) {
        List<Object[]> rows = exerciseRepository.countByPrimaryMuscle(viewer(userId));

        // 어깨 전·측면·후면은 같은 primary_muscle을 delt_region으로 가른다
        return Arrays.stream(BrowseCategory.values())
                .map(category -> new CategoryCount(category, category.label(),
                        rows.stream()
                                .filter(row -> category.primaryMuscles().contains((String) row[0]))
                                .filter(row -> category.deltRegion() == null || category.deltRegion() == row[1])
                                .mapToLong(row -> (Long) row[2])
                                .sum()))
                .toList();
    }

    /**
     * 한 분류 안의 계열 목록. 종목이 많은 계열이 앞에 온다.
     *
     * <p>계열 안에서 만든 내 종목은 그 계열 수에 들어가고, 계열 없이 만든 내 종목이 있으면 맨 끝에 "내 종목" 칸
     * 하나({@code mine})를 붙인다 — 계열 카드들 사이에 끼지 않게, 만들기 버튼 바로 위에.
     */
    public List<GroupCount> groups(BrowseCategory category, Long userId) {
        List<Object[]> rows = exerciseRepository.countByGroup(category.primaryMuscles(), category.deltRegion(),
                viewer(userId));

        // 대표 종목의 영문명은 계열 카드 그림의 파일명을 만드는 데 쓴다. 한 분류의
        // 계열이 많아야 열 몇 개라 한 번 더 읽어도 된다.
        Map<Long, String> names = new HashMap<>();
        for (Exercise exercise : exerciseRepository.findAllById(
                rows.stream().filter(row -> row[0] != null).map(row -> (Long) row[2]).toList())) {
            names.put(exercise.getId(), exercise.getNameEn());
        }

        List<GroupCount> result = new ArrayList<>();
        long ungroupedMine = 0;
        for (Object[] row : rows) {
            if (row[0] == null) {
                ungroupedMine = (Long) row[1];
                continue;
            }
            result.add(new GroupCount((String) row[0], (Long) row[1], (Long) row[2], names.get((Long) row[2]), false));
        }
        if (ungroupedMine > 0) {
            result.add(new GroupCount(null, ungroupedMine, null, null, true));
        }
        return result;
    }

    /** 종목 단건. 남의 내 종목·숨긴 내 종목은 404 */
    public ExerciseResponse get(Long userId, Long exerciseId) {
        Exercise exercise = mustFindVisible(userId, exerciseId);
        Set<Long> favorites = favoriteIds(userId, List.of(exercise));
        return toResponse(exercise, userId, favorites);
    }

    /**
     * 즐겨찾기 추가. <b>멱등하다</b> — 이미 등록돼 있으면 아무것도 하지 않는다.
     *
     * <p>UNIQUE 제약에 기대 예외를 삼키는 대신 먼저 확인한다. 별표는 연타되기 쉬운
     * 조작이라, 정상 흐름에서 예외가 나는 구조를 두면 로그가 오염된다.
     */
    @Transactional
    public void addFavorite(Long userId, Long exerciseId) {
        mustFindVisible(userId, exerciseId);
        if (favoriteRepository.existsByUserIdAndExerciseId(userId, exerciseId)) {
            return;
        }
        favoriteRepository.save(UserFavoriteExercise.of(userId, exerciseId));
    }

    /**
     * 즐겨찾기 해제. 등록돼 있지 않아도 성공으로 본다.
     *
     * <p>없는 종목이면 404다 — 잘못된 id를 조용히 성공으로 돌려주면 클라이언트가 오타를 알아채지 못한다.
     * "이미 해제된 상태"와 "없는 종목"은 다르다.
     */
    @Transactional
    public void removeFavorite(Long userId, Long exerciseId) {
        mustFindVisible(userId, exerciseId);
        favoriteRepository.deleteByUserIdAndExerciseId(userId, exerciseId);
    }

    // ── 내 종목

    /**
     * 만들기. 숨긴 내 종목과 이름이 같으면 새로 만들지 않고 그 종목을 되살려 값을 바꾼다 — 지난 기록이 다시 이어진다.
     */
    @Transactional
    public ExerciseResponse create(Long userId, CustomExerciseRequest request) {
        String name = normalizeName(request.nameKo());
        CustomTarget target = targetOf(request);
        String groupName = groupOf(request);
        if (exerciseRepository.existsByOwnerIdIsNullAndNameKo(name)) {
            throw new ApiException(ErrorCode.EXERCISE_NAME_TAKEN, "같은 이름의 기본 종목이 있어요. 그 종목을 골라 주세요.");
        }

        Optional<Exercise> same = exerciseRepository.findByOwnerIdAndNameKo(userId, name);
        Exercise exercise;
        if (same.isPresent()) {
            if (!same.get().isHidden()) {
                throw new ApiException(ErrorCode.EXERCISE_NAME_TAKEN, "이미 만든 종목 이름이에요.");
            }
            exercise = same.get();
            exercise.restore();
            exercise.change(name, target, request.equipment(), request.measureType(), groupName);
        } else {
            if (exerciseRepository.countByOwnerId(userId) >= CUSTOM_LIMIT) {
                throw new ApiException(ErrorCode.EXERCISE_LIMIT_EXCEEDED);
            }
            exercise = exerciseRepository.save(
                    Exercise.custom(userId, name, target, request.equipment(), request.measureType(), groupName));
        }
        return ExerciseResponse.of(exercise, favoriteRepository.existsByUserIdAndExerciseId(userId, exercise.getId()));
    }

    /** 고치기 — 자기 종목만. 부위를 바꾸면 지난 기록도 새 부위로 분석된다(분석은 그때그때 기록으로 계산한다) */
    @Transactional
    public ExerciseResponse update(Long userId, Long exerciseId, CustomExerciseRequest request) {
        Exercise exercise = mustFindOwn(userId, exerciseId);
        String name = normalizeName(request.nameKo());
        CustomTarget target = targetOf(request);
        String groupName = groupOf(request);
        if (!name.equals(exercise.getNameKo())) {
            if (exerciseRepository.existsByOwnerIdIsNullAndNameKo(name)) {
                throw new ApiException(ErrorCode.EXERCISE_NAME_TAKEN, "같은 이름의 기본 종목이 있어요.");
            }
            // 숨긴 종목과도 겹치면 안 된다 — 그 이름으로 다시 만들면 되살아나야 한다
            if (exerciseRepository.findByOwnerIdAndNameKo(userId, name).isPresent()) {
                throw new ApiException(ErrorCode.EXERCISE_NAME_TAKEN, "이미 만든 종목 이름이에요.");
            }
        }
        exercise.change(name, target, request.equipment(), request.measureType(), groupName);
        return ExerciseResponse.of(exercise, favoriteRepository.existsByUserIdAndExerciseId(userId, exerciseId));
    }

    /**
     * 지우기 — 자기 종목만. 이 종목으로 기록한 세트가 있으면 지우지 않고 숨긴다. 지난 기록의 종목 이름이 사라지면
     * 그날 무엇을 했는지 알 수 없다. 기록이 없으면 지운다(즐겨찾기도 함께 지워진다).
     */
    @Transactional
    public void delete(Long userId, Long exerciseId) {
        Exercise exercise = mustFindOwn(userId, exerciseId);
        if (exerciseRepository.hasSets(exerciseId)) {
            exercise.hide(OffsetDateTime.now(clock));
            return;
        }
        exerciseRepository.delete(exercise);
    }

    /** 앞뒤 공백을 걷고 여러 칸 띄어쓰기는 한 칸으로 — 겹침을 볼 때 "벤치  프레스"와 "벤치 프레스"는 같은 이름이다 */
    static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "이름을 적어 주세요.");
        }
        return name;
    }

    private static CustomTarget targetOf(CustomExerciseRequest request) {
        if (request.category() == BrowseCategory.LEGS && request.legSide() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "하체는 앞허벅지·뒤허벅지를 골라 주세요.");
        }
        return CustomTarget.of(request.category(),
                request.category() == BrowseCategory.LEGS ? request.legSide() : null);
    }

    /** 계열 안에서 만들면 그 계열. 그 분류에 실제로 있는 계열이어야 한다 */
    private String groupOf(CustomExerciseRequest request) {
        String groupName = request.groupName() == null || request.groupName().isBlank() ? null : request.groupName();
        if (groupName != null && !exerciseRepository.existsBuiltinGroup(groupName,
                request.category().primaryMuscles(), request.category().deltRegion())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "그 부위에 없는 계열이에요.");
        }
        return groupName;
    }

    private Exercise mustFindVisible(Long userId, Long exerciseId) {
        return exerciseRepository.findVisible(exerciseId, viewer(userId))
                .orElseThrow(() -> ApiException.notFound("종목"));
    }

    /** 자기 종목(숨긴 것 제외). 기본 종목·남의 종목은 404 — 고칠 수 있는 종목인지조차 드러내지 않는다 */
    private Exercise mustFindOwn(Long userId, Long exerciseId) {
        return exerciseRepository.findVisible(exerciseId, userId)
                .filter(exercise -> exercise.ownedBy(userId))
                .orElseThrow(() -> ApiException.notFound("종목"));
    }

    private static Long viewer(Long userId) {
        return userId == null ? ExerciseRepository.NOBODY : userId;
    }

    /** 부분 일치용 LIKE 패턴. 대소문자를 무시하려고 미리 소문자로 맞춘다. */
    private static String namePattern(String q) {
        return q == null ? null : "%" + q.toLowerCase() + "%";
    }

    private Set<Long> favoriteIds(Long userId, List<Exercise> exercises) {
        if (userId == null || exercises.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = exercises.stream().map(Exercise::getId).toList();
        return Set.copyOf(favoriteRepository.findFavoriteExerciseIds(userId, ids));
    }

    private ExerciseResponse toResponse(Exercise e, Long userId, Set<Long> favorites) {
        return ExerciseResponse.of(e, userId == null ? null : favorites.contains(e.getId()));
    }
}
