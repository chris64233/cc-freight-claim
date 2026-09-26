package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 结算请求：按责任分录（承运段）指定本次结算金额。
 * 每条金额为正、不得超过该分录认可分摊金额扣除已结算的余额。
 */
public record SettlementRequest(
        @NotBlank String settlementNo,
        String remark,
        @NotEmpty @Valid List<SettlementLineRequest> lines) {

    public record SettlementLineRequest(
            @NotNull Integer segmentSeq,
            @NotNull @Positive BigDecimal amount) {
    }
}
