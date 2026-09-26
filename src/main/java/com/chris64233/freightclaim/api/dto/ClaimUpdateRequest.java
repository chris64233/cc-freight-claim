package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;

import com.chris64233.freightclaim.domain.LossType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 修改索赔内容（损失金额/类型/描述），会递增内容版本。 */
public record ClaimUpdateRequest(
        @NotNull LossType lossType,
        @NotNull @Positive BigDecimal lossAmount,
        String description) {
}
