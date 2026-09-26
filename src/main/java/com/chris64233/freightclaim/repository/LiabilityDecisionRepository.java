package com.chris64233.freightclaim.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.freightclaim.domain.DecisionStatus;
import com.chris64233.freightclaim.domain.LiabilityDecision;

public interface LiabilityDecisionRepository extends JpaRepository<LiabilityDecision, Long> {

    List<LiabilityDecision> findByClaimIdOrderByIdAsc(Long claimId);

    Optional<LiabilityDecision> findFirstByClaimIdAndStatus(Long claimId, DecisionStatus status);

    /** 行锁读取，配合唯一索引确保并发确认不会产生两套结果。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from LiabilityDecision d where d.id = :id")
    Optional<LiabilityDecision> findByIdForUpdate(@Param("id") Long id);
}
