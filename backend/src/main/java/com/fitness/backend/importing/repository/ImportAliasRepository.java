package com.fitness.backend.importing.repository;

import com.fitness.backend.importing.domain.ImportAlias;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportAliasRepository extends JpaRepository<ImportAlias, Long> {

    List<ImportAlias> findByUserId(Long userId);

    /** 짝을 남긴다. 같은 이름이 이미 있으면 종목만 바꾼다 — 두 요청이 겹쳐도 유일 제약에 걸리지 않는다 */
    @Modifying
    @Query(value = """
            insert into exercise_import_aliases (user_id, source_name, exercise_id)
            values (:userId, :sourceName, :exerciseId)
            on conflict (user_id, source_name) do update set exercise_id = excluded.exercise_id
            """, nativeQuery = true)
    int upsert(@Param("userId") Long userId, @Param("sourceName") String sourceName,
               @Param("exerciseId") Long exerciseId);
}
