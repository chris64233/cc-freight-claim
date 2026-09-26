package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;

import com.chris64233.freightclaim.domain.LiabilityEntry;

public record LiabilityEntryResponse(
        Long id,
        Integer segmentSeq,
        String carrierCode,
        BigDecimal ratioWeight,
        BigDecimal allocatedAmount,
        BigDecimal settledAmount,
        BigDecimal recoveredAmount,
        BigDecimal reversedAmount) {

    public static LiabilityEntryResponse of(LiabilityEntry e) {
        return new LiabilityEntryResponse(e.getId(), e.getSegment().getSeq(), e.getSegment().getCarrierCode(),
                e.getRatioWeight(), e.getAllocatedAmount(), e.getSettledAmount(),
                e.getRecoveredAmount(), e.getReversedAmount());
    }

    public DecisionAllocationResponse toAllocation() {
        return new DecisionAllocationResponse(segmentSeq(), carrierCode(), ratioWeight(), allocatedAmount());
    }
}
