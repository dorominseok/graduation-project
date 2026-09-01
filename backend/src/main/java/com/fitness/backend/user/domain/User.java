package com.fitness.backend.user.domain;

import com.fitness.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자. {@code V1__init.sql} + {@code V3}의 {@code goal}.
 *
 * <p>비밀번호는 <b>해시만</b> 갖는다. 평문을 필드로 두면 로그·직렬화·디버거를 통해
 * 새어 나갈 경로가 생기므로 아예 저장하지 않는다.
 */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    /**
     * 훈련 목표. 회원가입 후 선택 입력이므로 {@code null} 허용.
     *
     * <p>{@code EnumType.STRING}이다. {@code ORDINAL}은 enum 상수의 순서가 바뀌면
     * 이미 저장된 값의 의미가 통째로 달라진다 — DB에는 {@code STRENGTH} 같은
     * 문자열이 들어가고 {@code ck_users_goal} CHECK 제약도 그 값을 검사한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TrainingGoal goal;

    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    /** 가입. 비밀번호는 호출부가 해시해서 넘긴다 — 엔티티가 인코더를 알 필요는 없다. */
    public static User signUp(String email, String passwordHash, String nickname) {
        return new User(email, passwordHash, nickname);
    }

    /** 프로필 부분 수정(명세 4.6). {@code null}은 "바꾸지 않음"이다. */
    public void updateNickname(String nickname) {
        if (nickname != null) {
            this.nickname = nickname;
        }
    }

    /**
     * 훈련 목표 변경. {@code null}로 되돌리는 것과 "바꾸지 않음"을 구분해야 하므로
     * 호출부가 갱신 여부를 판단해 이 메서드를 호출한다(명세 4.6).
     */
    public void changeGoal(TrainingGoal goal) {
        this.goal = goal;
    }
}
