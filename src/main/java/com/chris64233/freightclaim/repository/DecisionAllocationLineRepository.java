package com.chris64233.freightclaim.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.freightclaim.domain.DecisionAllocationLine;

public interface DecisionAllocationLineRepository extends JpaRepository<DecisionAllocationLine, Long> {

    List<DecisionAllocationLine> findByDecisionIdOrderByIdAsc(Long decisionId);

    void deleteByDecisionId(Long decisionId);
}
