package com.fitness.backend.importing.domain;

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
 * 맞춘 이름 기억(「기록가져오기_설계서」 결정 9). 사람마다 원래 이름 하나에 종목 하나.
 *
 * <p>읽기만 엔티티로 한다. 넣기는 같은 이름이 있으면 종목만 바꾸는 한 문장이라
 * {@code ImportAliasRepository#upsert}가 한다.
 */
@Entity
@Getter
@Table(name = "exercise_import_aliases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportAlias extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 원래 이름 전체(띄어쓰기 정리) — {@code 풀업: 중량} */
    @Column(name = "source_name", nullable = false, length = 200)
    private String sourceName;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;
}
