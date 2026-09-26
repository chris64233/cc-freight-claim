package com.chris64233.freightclaim.repo;

import com.chris64233.freightclaim.domain.LiabilityEntry;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiabilityEntryRepository extends JpaRepository<LiabilityEntry, Long> {
    List<LiabilityEntry> findByDecisionIdOrderByIdAsc(Long decisionId);

    /** 悲观行锁：串行化同一分录上的冲回，防止并发冲回超额。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from LiabilityEntry e where e.id = :id")
    Optional<LiabilityEntry> lockById(@Param("id") Long id);
}
