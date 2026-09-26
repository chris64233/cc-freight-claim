package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.chris64233.freightclaim.domain.Settlement;

public record SettlementResponse(
        Long id,
        String settlementNo,
        Long decisionId,
        BigDecimal totalAmount,
        Instant settledAt,
        String remark,
        List<SettlementLineResponse> lines) {

    public static SettlementResponse of(Settlement s, List<SettlementLineResponse> lines) {
        return new SettlementResponse(s.getId(), s.getSettlementNo(), s.getDecision().getId(),
                s.getTotalAmount(), s.getSettledAt(), s.getRemark(), lines);
    }
}
