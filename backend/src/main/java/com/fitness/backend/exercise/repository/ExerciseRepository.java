package com.fitness.backend.exercise.repository;

import com.fitness.backend.exercise.domain.BodyPart;
import com.fitness.backend.exercise.domain.Equipment;
import com.fitness.backend.exercise.domain.Exercise;
import com.fitness.backend.exercise.domain.MeasureType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /**
     * 종목 검색(명세 5.2). 넘기지 않은 조건은 {@code null}로 두면 무시된다.
     *
     * <p>정렬은 {@link Pageable}이 정한다 — 기본은 {@code nameKo,asc}이며 컨트롤러가 채운다.
     *
     * <p>{@code namePattern}은 이미 소문자로 바뀌고 {@code %}로 감싸인 값이다.
     * SQL 안에서 {@code concat('%', :q, '%')}로 만들지 않는 이유는, 검색어가 없을 때
     * 그 자리의 {@code null}이 어떤 타입인지 알 수 없어 PostgreSQL이
     * {@code lower(bytea)}를 찾다 실패하기 때문이다. {@code like}의 상대로 두면
     * 문자열임이 드러나 그 문제가 생기지 않는다.
     */
    @Query("""
            select e from Exercise e
             where (:namePattern is null
                    or lower(e.nameKo) like :namePattern
                    or lower(e.nameEn) like :namePattern)
               and (:bodyPart is null or e.bodyPart = :bodyPart)
               and (:equipment is null or e.equipment = :equipment)
               and (:measureType is null or e.measureType = :measureType)
            """)
    Page<Exercise> search(@Param("namePattern") String namePattern,
                          @Param("bodyPart") BodyPart bodyPart,
                          @Param("equipment") Equipment equipment,
                          @Param("measureType") MeasureType measureType,
                          Pageable pageable);

    /**
     * 즐겨찾기만 검색(명세 5.2의 세 번째 탭).
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
             order by f.createdAt desc
            """)
    Page<Exercise> searchFavorites(@Param("userId") Long userId,
                                   @Param("namePattern") String namePattern,
                                   @Param("bodyPart") BodyPart bodyPart,
                                   @Param("equipment") Equipment equipment,
                                   @Param("measureType") MeasureType measureType,
                                   Pageable pageable);
}
