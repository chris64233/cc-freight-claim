package com.chris64233.freightclaim.repo;

import com.chris64233.freightclaim.domain.Settlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByDecisionId(Long decisionId);

    boolean existsByDecisionClaimId(Long claimId);
}
