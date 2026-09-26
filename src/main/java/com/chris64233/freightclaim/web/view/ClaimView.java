package com.chris64233.freightclaim.web.view;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 索赔视图（索赔证据台账条目）。 */
public record ClaimView(
        Long id,
        String externalClaimNo,
        String shipmentNo,
        String lossEventRef,
        BigDecimal lossAmount,
        String lossType,
        String evidence,
        String status,
        int contentVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
