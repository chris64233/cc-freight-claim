package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.chris64233.freightclaim.domain.AdjustmentRecord;

public record AdjustmentResponse(
        Long id,
        Long liabilityEntryId,
        Integer segmentSeq,
        String carrierCode,
        String type,
        BigDecimal amount,
        String adjustmentRef,
        String remark,
        Instant createdAt) {

    public static AdjustmentResponse of(AdjustmentRecord r) {
        return new AdjustmentResponse(r.getId(), r.getLiabilityEntry().getId(),
                r.getLiabilityEntry().getSegment().getSeq(),
                r.getLiabilityEntry().getSegment().getCarrierCode(),
                r.getType().name(), r.getAmount(), r.getAdjustmentRef(), r.getRemark(), r.getCreatedAt());
    }
}
