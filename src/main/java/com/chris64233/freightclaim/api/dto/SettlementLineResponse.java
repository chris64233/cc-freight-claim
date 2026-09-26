package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;

import com.chris64233.freightclaim.domain.SettlementLine;

public record SettlementLineResponse(
        Long id,
        Integer segmentSeq,
        String carrierCode,
        BigDecimal amount) {

    public static SettlementLineResponse of(SettlementLine l) {
        return new SettlementLineResponse(l.getId(), l.getLiabilityEntry().getSegment().getSeq(),
                l.getLiabilityEntry().getSegment().getCarrierCode(), l.getAmount());
    }
}
