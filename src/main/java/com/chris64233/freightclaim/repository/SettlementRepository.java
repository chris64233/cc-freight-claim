package com.chris64233.freightclaim.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.freightclaim.domain.Settlement;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findBySettlementNo(String settlementNo);

    List<Settlement> findByDecisionIdOrderByIdAsc(Long decisionId);
}
