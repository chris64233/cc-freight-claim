package com.chris64233.freightclaim.web.request;

import com.chris64233.freightclaim.domain.LossType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ClaimRequest(
        @NotBlank String shipmentNo,
        @NotBlank String externalClaimNo,
        @NotBlank String lossEventRef,
        @NotNull @Positive BigDecimal lossAmount,
        @NotNull LossType lossType,
        @NotBlank String evidence) {
}
