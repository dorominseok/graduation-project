package com.fitness.backend.exercise.repository;

import com.fitness.backend.exercise.domain.UserFavoriteExercise;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFavoriteExerciseRepository extends JpaRepository<UserFavoriteExercise, Long> {

    boolean existsByUserIdAndExerciseId(Long userId, Long exerciseId);

    void deleteByUserIdAndExerciseId(Long userId, Long exerciseId);

    /**
     * 지금 페이지에 실린 종목 중 즐겨찾기인 것들의 id.
     *
     * <p>행마다 따로 묻지 않고 한 번에 받아 {@code isFavorite}를 채운다(명세 5.2).
     * 페이지 크기만큼만 in 절에 들어가므로 목록이 커져도 질의는 두 번으로 고정된다.
     */
    @Query("""
            select f.exerciseId from UserFavoriteExercise f
             where f.userId = :userId
               and f.exerciseId in :exerciseIds
            """)
    List<Long> findFavoriteExerciseIds(@Param("userId") Long userId,
                                       @Param("exerciseIds") Collection<Long> exerciseIds);
}
