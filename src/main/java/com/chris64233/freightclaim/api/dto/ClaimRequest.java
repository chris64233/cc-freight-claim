package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;

import com.chris64233.freightclaim.domain.LossType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClaimRequest(
        @NotBlank String externalClaimNo,
        @NotBlank String shipmentNo,
        @NotBlank String lossEventNo,
        @NotNull LossType lossType,
        @NotNull @Positive BigDecimal lossAmount,
        String description,
        java.util.List<ClaimEvidenceRequest> evidences) {
}
