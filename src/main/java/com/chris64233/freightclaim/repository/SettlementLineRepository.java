package com.chris64233.freightclaim.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.freightclaim.domain.SettlementLine;

public interface SettlementLineRepository extends JpaRepository<SettlementLine, Long> {

    List<SettlementLine> findBySettlementIdOrderByIdAsc(Long settlementId);

    List<SettlementLine> findByLiabilityEntryDecisionClaimIdOrderByIdAsc(Long claimId);
}
