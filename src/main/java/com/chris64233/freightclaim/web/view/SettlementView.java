package com.chris64233.freightclaim.web.view;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 结算台账视图。 */
public record SettlementView(
        Long id,
        Long decisionId,
        BigDecimal settledAmount,
        OffsetDateTime settledAt,
        String remark) {
}
