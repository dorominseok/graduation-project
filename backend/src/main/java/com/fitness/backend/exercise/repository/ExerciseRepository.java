package com.fitness.backend.exercise.repository;

import com.fitness.backend.exercise.domain.BodyPart;
import com.fitness.backend.exercise.domain.DeltRegion;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /**
     * 종목 검색. 넘기지 않은 조건은 {@code null}로 두면 무시된다.
     *
     * <p>정렬은 {@link Pageable}이 정한다 — 기본은 {@code nameKo,asc}이며 컨트롤러가 채운다.
     *
     * <p>{@code namePattern}은 이미 소문자로 바뀌고 {@code %}로 감싸인 값이다.
     * SQL 안에서 {@code concat('%', :q, '%')}로 만들지 않는 이유는, 검색어가 없을 때
     * 그 자리의 {@code null}이 어떤 타입인지 알 수 없어 PostgreSQL이
     * {@code lower(bytea)}를 찾다 실패하기 때문이다. {@code like}의 상대로 두면
     * 문자열임이 드러나 그 문제가 생기지 않는다.
     *
     * <p><b>보이는 범위</b>: 기본 종목과, {@code viewerId}가 만든 숨기지 않은 내 종목. 비로그인은
     * {@code viewerId}에 {@link #NOBODY}를 넘겨 기본 종목만 본다. {@code ungroupedMine}이면 계열 없이 만든
     * 내 종목만(부위 화면의 "내 종목" 칸).
     */
    @Query("""
            select e from Exercise e
             where (:namePattern is null
                    or lower(e.nameKo) like :namePattern
                    or lower(e.nameEn) like :namePattern)
               and (:bodyPart is null or e.bodyPart = :bodyPart)
               and (:equipment is null or e.equipment = :equipment)
               and (:measureType is null or e.measureType = :measureType)
               and (:allMuscles = true or e.primaryMuscle in :muscles)
               and (:groupName is null or e.groupName = :groupName)
               and (:deltRegion is null or e.deltRegion = :deltRegion)
               and (e.ownerId is null or (e.ownerId = :viewerId and e.hiddenAt is null))
               and (:ungroupedMine = false or (e.ownerId = :viewerId and e.groupName is null))
            """)
    Page<Exercise> search(@Param("namePattern") String namePattern,
                          @Param("bodyPart") BodyPart bodyPart,
                          @Param("equipment") Equipment equipment,
                          @Param("measureType") MeasureType measureType,
                          @Param("allMuscles") boolean allMuscles,
                          @Param("muscles") Collection<String> muscles,
                          @Param("groupName") String groupName,
                          @Param("deltRegion") DeltRegion deltRegion,
                          @Param("viewerId") Long viewerId,
                          @Param("ungroupedMine") boolean ungroupedMine,
                          Pageable pageable);

    /**
     * 즐겨찾기만 검색.
     *
     * <p>정렬이 <b>별표를 누른 순</b>이라 {@link Pageable}의 sort를 쓰지 않고 쿼리에
     * 고정한다 — 기준 컬럼이 {@code exercises}가 아니라 {@code user_favorite_exercises}의
     * {@code created_at}이라서, 밖에서 넘기는 sort로는 가리킬 수가 없다.
     */
    @Query("""
            select e from Exercise e
              join UserFavoriteExercise f on f.exerciseId = e.id and f.userId = :userId
             where (:namePattern is null
                    or lower(e.nameKo) like :namePattern
                    or lower(e.nameEn) like :namePattern)
               and (:bodyPart is null or e.bodyPart = :bodyPart)
               and (:equipment is null or e.equipment = :equipment)
               and (:measureType is null or e.measureType = :measureType)
               and (:allMuscles = true or e.primaryMuscle in :muscles)
               and (:groupName is null or e.groupName = :groupName)
               and (:deltRegion is null or e.deltRegion = :deltRegion)
               and (e.ownerId is null or (e.ownerId = :userId and e.hiddenAt is null))
             order by f.createdAt desc
            """)
    Page<Exercise> searchFavorites(@Param("userId") Long userId,
                                   @Param("namePattern") String namePattern,
                                   @Param("bodyPart") BodyPart bodyPart,
                                   @Param("equipment") Equipment equipment,
                                   @Param("measureType") MeasureType measureType,
                                   @Param("allMuscles") boolean allMuscles,
                                   @Param("muscles") Collection<String> muscles,
                                   @Param("groupName") String groupName,
                                   @Param("deltRegion") DeltRegion deltRegion,
                                   Pageable pageable);

    /**
     * 분류별 종목 수. 부위 그리드의 숫자다.
     *
     * <p>{@code primary_muscle}로 세어 돌려주고 12종으로 묶는 것은 서비스가 한다 —
     * 묶는 규칙이 {@link com.fitness.backend.exercise.domain.BrowseCategory}에 있어서다.
     * 내 종목은 만든 사람에게만 센다.
     */
    @Query("""
            select e.primaryMuscle, e.deltRegion, count(e) from Exercise e
             where e.ownerId is null or (e.ownerId = :viewerId and e.hiddenAt is null)
             group by e.primaryMuscle, e.deltRegion
            """)
    List<Object[]> countByPrimaryMuscle(@Param("viewerId") Long viewerId);

    /**
     * 한 분류 안의 계열별 종목 수.
     *
     * <p>{@code min(e.id)}는 대표 종목이다 — 계열 카드에 이미지를 붙일 때 어느 종목의
     * 것을 쓸지 정해야 하고, 지금은 이미지가 없으니 자리만 잡아둔다. 내 종목은 기본 종목보다 id가
     * 커서 대표가 되지 않는다.
     *
     * <p>계열 안에서 만든 내 종목은 그 계열 수에 들어가고, 계열 없이 만든 내 종목은
     * {@code groupName}이 {@code null}인 한 줄로 나온다 — 서비스가 "내 종목" 칸으로 바꾼다.
     */
    @Query("""
            select e.groupName, count(e), min(e.id)
              from Exercise e
             where e.primaryMuscle in :muscles
               and (:deltRegion is null or e.deltRegion = :deltRegion)
               and (e.ownerId is null or (e.ownerId = :viewerId and e.hiddenAt is null))
             group by e.groupName
             order by count(e) desc, e.groupName asc
            """)
    List<Object[]> countByGroup(@Param("muscles") Collection<String> muscles,
                                @Param("deltRegion") DeltRegion deltRegion,
                                @Param("viewerId") Long viewerId);

    // ── 내 종목

    /** 비로그인 요청의 {@code viewerId}. 어떤 종목의 주인도 아니다 */
    long NOBODY = -1L;

    /** 이 사용자가 고르고 열 수 있는 종목 — 기본 종목, 또는 숨기지 않은 자기 종목. 아니면 비어 있다(남의 것은 404) */
    @Query("""
            select e from Exercise e
             where e.id = :id
               and (e.ownerId is null or (e.ownerId = :viewerId and e.hiddenAt is null))
            """)
    Optional<Exercise> findVisible(@Param("id") Long id, @Param("viewerId") Long viewerId);

    /** 이 사용자의 기록·루틴 화면에서 이름과 부위를 찾을 종목 — 기본 종목과 자기 종목(숨긴 것까지) */
    @Query("select e from Exercise e where e.ownerId is null or e.ownerId = :userId")
    List<Exercise> findAllForUser(@Param("userId") Long userId);

    boolean existsByOwnerIdIsNullAndNameKo(String nameKo);

    /** 숨긴 것까지 — 숨긴 이름으로 다시 만들면 되살린다 */
    Optional<Exercise> findByOwnerIdAndNameKo(Long ownerId, String nameKo);

    /** 숨긴 것까지 센다(상한 100) */
    long countByOwnerId(Long ownerId);

    /** 그 분류에 실제로 있는 계열인가 — 계열 안에서 만들 때 */
    @Query("""
            select count(e) > 0 from Exercise e
             where e.ownerId is null
               and e.groupName = :groupName
               and e.primaryMuscle in :muscles
               and (:deltRegion is null or e.deltRegion = :deltRegion)
            """)
    boolean existsBuiltinGroup(@Param("groupName") String groupName,
                               @Param("muscles") Collection<String> muscles,
                               @Param("deltRegion") DeltRegion deltRegion);

    /** 이 종목으로 기록한 세트가 있는가 — 있으면 지우지 않고 숨긴다 */
    @Query(value = "select exists(select 1 from workout_sets where exercise_id = :id)", nativeQuery = true)
    boolean hasSets(@Param("id") Long id);
}
