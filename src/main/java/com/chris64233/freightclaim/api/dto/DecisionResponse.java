package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.chris64233.freightclaim.domain.LiabilityDecision;

public record DecisionResponse(
        Long id,
        Long claimId,
        BigDecimal approvedAmount,
        String status,
        long basedContentVersion,
        long basedEvidenceVersion,
        Instant confirmedAt,
        String remark,
        List<DecisionAllocationResponse> allocations) {

    public static DecisionResponse draft(LiabilityDecision d, List<DecisionAllocationResponse> allocations) {
        return new DecisionResponse(d.getId(), d.getClaim().getId(), d.getApprovedAmount(),
                d.getStatus().name(), d.getBasedContentVersion(), d.getBasedEvidenceVersion(),
                d.getConfirmedAt(), d.getRemark(), allocations);
    }

    public static DecisionResponse confirmed(LiabilityDecision d, List<LiabilityEntryResponse> entries) {
        return new DecisionResponse(d.getId(), d.getClaim().getId(), d.getApprovedAmount(),
                d.getStatus().name(), d.getBasedContentVersion(), d.getBasedEvidenceVersion(),
                d.getConfirmedAt(), d.getRemark(),
                entries.stream().map(LiabilityEntryResponse::toAllocation).toList());
    }
}
