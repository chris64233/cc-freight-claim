package com.chris64233.freightclaim.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.freightclaim.domain.ClaimEvidence;

public interface ClaimEvidenceRepository extends JpaRepository<ClaimEvidence, Long> {

    List<ClaimEvidence> findByClaimIdOrderByIdAsc(Long claimId);

    boolean existsByClaimIdAndEvidenceRef(Long claimId, String evidenceRef);
}
