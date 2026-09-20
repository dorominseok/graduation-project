package com.fitness.backend.exercise.domain;

import com.fitness.backend.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 즐겨찾기. 부록 A {@code user_favorite_exercises} (V1).
 *
 * <p>연관관계 대신 {@code userId}·{@code exerciseId}를 값으로 갖는다.
 * {@link com.fitness.backend.auth.domain.RefreshToken}과 같은 방식이며, 이 테이블을
 * 읽는 곳이 "그 사용자의 즐겨찾기 종목 id 집합"뿐이라 객체 그래프를 만들 이유가 없다.
 *
 * <p>{@code (user_id, exercise_id)}에 UNIQUE가 걸려 있어 중복 등록은 DB가 막는다.
 * 5.4가 멱등을 요구하므로 서비스는 넣기 전에 존재 여부를 본다.
 */
@Entity
@Getter
@Table(name = "user_favorite_exercises")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavoriteExercise extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    private UserFavoriteExercise(Long userId, Long exerciseId) {
        this.userId = userId;
        this.exerciseId = exerciseId;
    }

    public static UserFavoriteExercise of(Long userId, Long exerciseId) {
        return new UserFavoriteExercise(userId, exerciseId);
    }
}
