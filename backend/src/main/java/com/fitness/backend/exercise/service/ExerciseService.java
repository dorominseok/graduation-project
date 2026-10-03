package com.fitness.backend.exercise.service;

import com.fitness.backend.analysis.domain.MuscleGroup;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.exercise.domain.BodyPart;
import com.fitness.backend.exercise.domain.BrowseCategory;
import com.fitness.backend.exercise.domain.DeltRegion;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import com.fitness.backend.exercise.domain.UserFavoriteExercise;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.exercise.repository.UserFavoriteExerciseRepository;
import com.fitness.backend.exercise.web.ExerciseDtos.CategoryCount;
import com.fitness.backend.exercise.web.ExerciseDtos.ExerciseResponse;
import com.fitness.backend.exercise.web.ExerciseDtos.GroupCount;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 조회와 즐겨찾기. 명세 5.2~5.4.
 *
 * <p>종목 자체는 읽기 전용이라 쓰기는 즐겨찾기 두 개뿐이다(5.1).
 */
@Service
@Transactional(readOnly = true)
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final UserFavoriteExerciseRepository favoriteRepository;

    public ExerciseService(ExerciseRepository exerciseRepository,
                           UserFavoriteExerciseRepository favoriteRepository) {
        this.exerciseRepository = exerciseRepository;
        this.favoriteRepository = favoriteRepository;
    }

    /**
     * 종목 검색(명세 5.2).
     *
     * @param userId       인증된 사용자. 비인증이면 {@code null}이고 {@code isFavorite}를 담지 않는다
     * @param favoriteOnly 즐겨찾기만. 비인증 요청에서는 무시한다 — 기준이 될 사용자가 없다
     */
    public Page<ExerciseResponse> search(Long userId, String q, BodyPart bodyPart,
                                         Equipment equipment, MeasureType measureType,
                                         BrowseCategory category, MuscleGroup muscleGroup,
                                         String groupName, boolean favoriteOnly, Pageable pageable) {
        String namePattern = namePattern(q);
        // 분류도 판정 부위도 없으면 in 절을 건너뛴다. 빈 컬렉션을 넘기면 아무것도 안 나온다.
        boolean allMuscles = category == null && muscleGroup == null;
        Collection<String> muscles = allMuscles ? List.of("") : musclesOf(category, muscleGroup);
        DeltRegion deltRegion = muscleGroup == null ? null : muscleGroup.deltRegion();

        Page<Exercise> page = favoriteOnly && userId != null
                ? exerciseRepository.searchFavorites(userId, namePattern, bodyPart, equipment, measureType,
                        allMuscles, muscles, groupName, deltRegion, pageable)
                : exerciseRepository.search(namePattern, bodyPart, equipment, measureType,
                        allMuscles, muscles, groupName, deltRegion, pageable);

        Set<Long> favorites = favoriteIds(userId, page.getContent());
        return page.map(e -> toResponse(e, userId, favorites));
    }

    /**
     * 탐색 분류와 판정 부위를 같이 주면 둘 다 만족하는 근육만 남긴다.
     *
     * <p>둘은 다른 축이다(LOG-24). 판정 부위 "뒤허벅지·둔근"은 탐색 분류의 하체와
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
     * 부위 그리드(LOG-24). 12종 분류와 각 종목 수를 준다.
     *
     * <p>종목이 없는 분류도 담는다 — 그리드에서 칸이 사라지면 자리가 밀려
     * 사용자가 위치로 기억한 것을 다시 찾아야 한다.
     */
    public List<CategoryCount> categories() {
        Map<String, Long> byMuscle = exerciseRepository.countByPrimaryMuscle().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        return Arrays.stream(BrowseCategory.values())
                .map(category -> new CategoryCount(category, category.label(),
                        category.primaryMuscles().stream()
                                .mapToLong(muscle -> byMuscle.getOrDefault(muscle, 0L))
                                .sum()))
                .toList();
    }

    /** 한 분류 안의 계열 목록(LOG-24). 종목이 많은 계열이 앞에 온다. */
    public List<GroupCount> groups(BrowseCategory category) {
        List<Object[]> rows = exerciseRepository.countByGroup(category.primaryMuscles());

        // 대표 종목의 영문명은 계열 카드 그림의 파일명을 만드는 데 쓴다. 한 분류의
        // 계열이 많아야 열 몇 개라 한 번 더 읽어도 된다.
        Map<Long, String> names = new HashMap<>();
        for (Exercise exercise : exerciseRepository.findAllById(
                rows.stream().map(row -> (Long) row[2]).toList())) {
            names.put(exercise.getId(), exercise.getNameEn());
        }

        return rows.stream()
                .map(row -> new GroupCount((String) row[0], (Long) row[1], (Long) row[2],
                        names.get((Long) row[2])))
                .toList();
    }

    /** 종목 단건(명세 5.3). */
    public ExerciseResponse get(Long userId, Long exerciseId) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> ApiException.notFound("종목"));
        Set<Long> favorites = favoriteIds(userId, List.of(exercise));
        return toResponse(exercise, userId, favorites);
    }

    /**
     * 즐겨찾기 추가(명세 5.4). <b>멱등하다</b> — 이미 등록돼 있으면 아무것도 하지 않는다.
     *
     * <p>UNIQUE 제약에 기대 예외를 삼키는 대신 먼저 확인한다. 별표는 연타되기 쉬운
     * 조작이라, 정상 흐름에서 예외가 나는 구조를 두면 로그가 오염된다.
     */
    @Transactional
    public void addFavorite(Long userId, Long exerciseId) {
        requireExercise(exerciseId);
        if (favoriteRepository.existsByUserIdAndExerciseId(userId, exerciseId)) {
            return;
        }
        favoriteRepository.save(UserFavoriteExercise.of(userId, exerciseId));
    }

    /** 즐겨찾기 해제(명세 5.4). 등록돼 있지 않아도 성공으로 본다. */
    @Transactional
    public void removeFavorite(Long userId, Long exerciseId) {
        requireExercise(exerciseId);
        favoriteRepository.deleteByUserIdAndExerciseId(userId, exerciseId);
    }

    /**
     * 없는 종목에 별표를 누르면 404다(명세 5.4).
     *
     * <p>해제에서도 확인하는 것은, 잘못된 id를 조용히 성공으로 돌려주면 클라이언트가
     * 오타를 알아채지 못하기 때문이다. "이미 해제된 상태"와 "없는 종목"은 다르다.
     */
    private void requireExercise(Long exerciseId) {
        if (!exerciseRepository.existsById(exerciseId)) {
            throw ApiException.notFound("종목");
        }
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
