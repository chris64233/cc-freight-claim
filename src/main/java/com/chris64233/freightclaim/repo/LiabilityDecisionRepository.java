package com.chris64233.freightclaim.repo;

import com.chris64233.freightclaim.domain.LiabilityDecision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiabilityDecisionRepository extends JpaRepository<LiabilityDecision, Long> {
    Optional<LiabilityDecision> findByClaimId(Long claimId);
}
