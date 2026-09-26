package com.chris64233.freightclaim.web.view;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 结算后调整（追偿/冲回）台账视图。 */
public record AdjustmentView(
        Long id,
        Long entryId,
        Long segmentId,
        String carrierCode,
        String type,
        BigDecimal amount,
        String reason,
        OffsetDateTime createdAt) {
}
