package com.fitness.backend.group.repository;

import com.fitness.backend.group.domain.GroupMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    /** 그룹 화면의 멤버 순서. 먼저 들어온 사람이 위다 */
    List<GroupMember> findByGroupIdOrderByJoinedAtAscIdAsc(Long groupId);

    /** 내 그룹 목록. 먼저 들어간 그룹이 위다 */
    List<GroupMember> findByUserIdOrderByJoinedAtAscIdAsc(Long userId);

    List<GroupMember> findByGroupIdIn(Collection<Long> groupIds);

    long countByGroupId(Long groupId);

    long countByUserId(Long userId);

    /**
     * 방장이 나갈 때 넘겨받을 사람 — 나가는 사람을 뺀 멤버 중 가장 먼저 들어온 사람.
     * 같은 시각에 들어왔으면 먼저 저장된 쪽이다.
     */
    Optional<GroupMember> findFirstByGroupIdAndUserIdNotOrderByJoinedAtAscIdAsc(Long groupId, Long userId);
}
