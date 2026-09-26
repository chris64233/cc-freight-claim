package com.chris64233.freightclaim.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.freightclaim.domain.AdjustmentRecord;

public interface AdjustmentRecordRepository extends JpaRepository<AdjustmentRecord, Long> {

    List<AdjustmentRecord> findByLiabilityEntryIdOrderByIdAsc(Long liabilityEntryId);

    List<AdjustmentRecord> findByLiabilityEntryDecisionClaimIdOrderByIdAsc(Long claimId);
}
