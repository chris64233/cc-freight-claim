package com.chris64233.freightclaim.web.view;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 责任决定 + 责任分摊台账视图。 */
public record DecisionView(
        Long id,
        String externalClaimNo,
        String status,
        BigDecimal approvedAmount,
        int basedEvidenceVersion,
        int basedClaimVersion,
        int currentEvidenceVersion,
        int currentClaimVersion,
        /** 决定所依据版本是否已落后（true 时确认/结算将返回冲突，需要重开重做）。 */
        boolean stale,
        boolean settled,
        List<EntryView> entries,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime confirmedAt) {
}
