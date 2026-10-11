package com.fitness.backend.group.repository;

import com.fitness.backend.group.domain.FriendGroup;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendGroupRepository extends JpaRepository<FriendGroup, Long> {

    Optional<FriendGroup> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /** 회원 탈퇴 때 방장을 넘기거나 그룹을 지울 대상 */
    List<FriendGroup> findByOwnerId(Long ownerId);

    /**
     * 멤버가 바뀌는 일(참여·나가기·내보내기·삭제)은 그룹 행을 잠그고 한다.
     *
     * <p>"세어 보고 넣기", "다음 방장을 골라 넘기기"는 두 단계라, 같은 그룹에서 둘이 동시에 하면
     * 둘 다 옛 상태를 보고 판단한다 — 19명인 그룹에 둘이 함께 들어와 21명이 되거나, 방장과
     * 다음 방장이 함께 나가 멤버가 아닌 사람이 방장으로 남는다. 그룹 하나 단위로 줄을 세운다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from FriendGroup g where g.id = :id")
    Optional<FriendGroup> findForUpdate(@Param("id") Long id);
}
