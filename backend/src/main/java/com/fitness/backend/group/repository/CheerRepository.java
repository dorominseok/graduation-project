package com.fitness.backend.group.repository;

import com.fitness.backend.group.domain.Cheer;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheerRepository extends JpaRepository<Cheer, Long> {

    /**
     * 응원 넣기. 이미 있으면 아무것도 하지 않는다.
     *
     * <p>"있는지 보고 넣기"로 하면 연타한 두 요청이 둘 다 "없음"을 보고 넣다가 하나가 유니크
     * 제약에 걸린다. 그 예외를 잡아도 트랜잭션은 이미 롤백으로 표시돼 500이 되므로, DB가 한
     * 문장 안에서 판단하게 한다.
     *
     * @return 새로 넣었으면 1, 이미 있었으면 0
     */
    @Modifying
    @Query(value = """
            INSERT INTO cheers (session_id, user_id) VALUES (:sessionId, :userId)
            ON CONFLICT (session_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    @Modifying
    @Query("delete from Cheer c where c.sessionId = :sessionId and c.userId = :userId")
    int deleteBySessionIdAndUserId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    long countBySessionId(Long sessionId);

    /** 세션마다 세면 멤버 수만큼 쿼리가 나간다. 한 번에 묶어 센다 */
    @Query("""
            select new com.fitness.backend.group.repository.CheerCount(c.sessionId, count(c))
              from Cheer c
             where c.sessionId in :sessionIds
             group by c.sessionId
            """)
    List<CheerCount> countBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    /** 이 중 내가 응원한 세션 */
    @Query("select c.sessionId from Cheer c where c.userId = :userId and c.sessionId in :sessionIds")
    List<Long> findCheeredSessionIds(@Param("userId") Long userId,
                                     @Param("sessionIds") Collection<Long> sessionIds);
}
