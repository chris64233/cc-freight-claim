package com.chris64233.freightclaim.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.freightclaim.domain.LiabilityEntry;

public interface LiabilityEntryRepository extends JpaRepository<LiabilityEntry, Long> {

    List<LiabilityEntry> findByDecisionIdOrderByIdAsc(Long decisionId);

    /** 结算/调整时对相关分录加行锁，防止并发结算/冲回超额。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from LiabilityEntry e where e.id = :id")
    java.util.Optional<LiabilityEntry> findByIdForUpdate(@Param("id") Long id);
}
