package com.fitness.backend.workout.repository;

import com.fitness.backend.workout.domain.WorkoutSession;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루 기록.
 *
 * <p><b>운동한 날은 세트가 하나라도 있는 기록</b>이다. 기록 화면을 열고 아무것도 체크하지 않거나
 * 세트를 다 지우면 세트 0개인 기록이 남는데, 목록·캘린더·횟수·그룹에는 나오지 않아야 한다.
 * 그래서 여러 날을 읽는 쿼리는 세트가 있는지를 함께 본다.
 */
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    /**
     * 소유권까지 한 번에 본다. {@code findById} 후 {@code userId}를 비교하면
     * 남의 기록이 존재하는지가 404와 403으로 갈려 드러나므로, 조회 단계에서 묶는다.
     */
    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    /** 그날의 기록. 하루에 하나뿐이다 */
    Optional<WorkoutSession> findByUserIdAndPerformedOn(Long userId, LocalDate performedOn);

    boolean existsByUserIdAndPerformedOn(Long userId, LocalDate performedOn);

    /**
     * 그날 기록이 없으면 만든다. 이미 있으면 아무것도 하지 않는다.
     *
     * <p>"있는지 보고 넣기"를 하면 기록 화면을 두 번 빠르게 열 때 둘 다 "없음"을 보고 넣어
     * 유니크 제약에 걸린다. 그 예외를 잡아도 트랜잭션이 이미 롤백으로 표시돼 500이 되므로
     * 충돌은 DB가 조용히 넘기게 한다(응원과 같은 방식).
     *
     * @return 새로 만들었으면 1, 이미 있었으면 0
     */
    @Transactional
    @Modifying
    @Query(value = """
            insert into workout_sessions (user_id, performed_on)
            values (:userId, :performedOn)
            on conflict (user_id, performed_on) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("performedOn") LocalDate performedOn);

    /**
     * 여러 사람의 기간 내 운동한 날. 그룹 멤버마다 조회하면 멤버 수만큼 쿼리가 나가므로
     * 한 번에 읽어 메모리에서 사람별로 묶는다. 일주일 치라 많아야 수십 건이다.
     */
    @Query("""
            select s from WorkoutSession s
             where s.userId in :userIds
               and s.performedOn >= :from
               and s.performedOn <= :to
               and exists (select 1 from WorkoutSet w where w.sessionId = s.id)
            """)
    List<WorkoutSession> findRecorded(@Param("userIds") Collection<Long> userIds,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /**
     * 히스토리 목록. 운동한 날만 준다.
     *
     * <p>{@code exerciseId}는 <b>날 단위 필터</b>다 — 그 종목의 세트가 하나라도 있는
     * 날을 고를 뿐, 응답에서 다른 종목의 세트를 빼지 않는다. {@code null}이면 조건을 걸지 않는다.
     */
    @Query("""
            select s from WorkoutSession s
            where s.userId = :userId
              and s.performedOn >= :from
              and s.performedOn <= :to
              and exists (select 1 from WorkoutSet w
                          where w.sessionId = s.id
                            and (:exerciseId is null or w.exerciseId = :exerciseId))
            """)
    Page<WorkoutSession> search(@Param("userId") Long userId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("exerciseId") Long exerciseId,
                                Pageable pageable);

    /**
     * 그 종목을 마지막으로 수행한 날. {@code before} 전날까지에서 찾는다.
     *
     * <p>오늘을 빼는 것은 프리필이 "지난번에 한 것"을 채우는 기능이기 때문이다. 하루 기록에는
     * 오늘 앞서 한 세트도 들어 있어서, 빼지 않으면 오늘 기록이 "직전 수행"으로 올라온다.
     *
     * <p>정렬이 {@code performedOn}인 것은 사후 입력 때문이다 — 어제 운동을 오늘 입력하면 저장
     * 시각이 가장 최근이 되므로, 저장 시각으로 정렬하면 오늘 입력한 과거 기록이 올라온다.
     */
    @Query("""
            select s from WorkoutSession s
            where s.userId = :userId
              and s.performedOn < :before
              and exists (select 1 from WorkoutSet w
                          where w.sessionId = s.id and w.exerciseId = :exerciseId)
            order by s.performedOn desc
            """)
    List<WorkoutSession> findLastPerformed(@Param("userId") Long userId,
                                           @Param("exerciseId") Long exerciseId,
                                           @Param("before") LocalDate before,
                                           Pageable pageable);

    /** 그 종목의 세트가 있는 날. 오늘 포함, 운동 날짜 최신순 */
    @Query("""
            select s from WorkoutSession s
            where s.userId = :userId
              and exists (select 1 from WorkoutSet w
                          where w.sessionId = s.id and w.exerciseId = :exerciseId)
            order by s.performedOn desc
            """)
    Page<WorkoutSession> findPerformed(@Param("userId") Long userId,
                                       @Param("exerciseId") Long exerciseId,
                                       Pageable pageable);

    /**
     * 판정 신뢰도({@code confidence})용 운동한 날 수.
     *
     * <p>세트가 아니라 <b>날</b>을 센다. 같은 기간에 세트 40개가 쌓였어도 그게
     * 이틀에서 나왔다면 주당 평균이라는 말 자체가 성립하지 않는다.
     */
    @Query("""
            select count(s) from WorkoutSession s
             where s.userId = :userId
               and s.performedOn >= :from
               and s.performedOn <= :to
               and exists (select 1 from WorkoutSet w where w.sessionId = s.id)
            """)
    int countRecordedDays(@Param("userId") Long userId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    /**
     * 기준일 이전(포함) 첫 운동한 날. 주당 평균의 분모를 정한다.
     *
     * <p>집계 구간(28일) 안이 아니라 <b>전체 기록</b>에서 찾는다. 두 달 전에 시작한
     * 사람이 최근 28일 중 앞 2주를 쉬었다면, 그 2주도 평균에 들어가야 맞다 —
     * 구간 안의 첫 기록일로 자르면 쉰 기간이 지워진다.
     */
    @Query("""
            select min(s.performedOn) from WorkoutSession s
             where s.userId = :userId
               and s.performedOn <= :to
               and exists (select 1 from WorkoutSet w where w.sessionId = s.id)
            """)
    LocalDate findFirstRecordedOn(@Param("userId") Long userId, @Param("to") LocalDate to);
}
