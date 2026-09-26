package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;

import com.chris64233.freightclaim.domain.AdjustmentType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 结算后追加调整（追偿/冲回）。
 * 追偿累计不超过该承运段认可分摊；冲回累计不超过该承运段已结算责任。
 */
public record AdjustmentRequest(
        @NotNull Integer segmentSeq,
        @NotNull AdjustmentType type,
        @NotNull @Positive BigDecimal amount,
        String adjustmentRef,
        String remark) {
}
