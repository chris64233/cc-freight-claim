package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.chris64233.freightclaim.domain.Claim;

public record ClaimResponse(
        Long id,
        String externalClaimNo,
        String shipmentNo,
        String lossEventNo,
        String lossType,
        BigDecimal lossAmount,
        String description,
        String status,
        long contentVersion,
        long shipmentEvidenceVersion,
        Instant createdAt,
        Instant updatedAt) {

    public static ClaimResponse of(Claim c) {
        return new ClaimResponse(c.getId(), c.getExternalClaimNo(), c.getShipment().getShipmentNo(),
                c.getLossEventNo(), c.getLossType().name(), c.getLossAmount(), c.getDescription(),
                c.getStatus().name(), c.getContentVersion(), c.getShipmentEvidenceVersion(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
