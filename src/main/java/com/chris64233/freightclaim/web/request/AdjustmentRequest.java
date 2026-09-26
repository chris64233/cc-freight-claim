package com.chris64233.freightclaim.web.request;

import com.chris64233.freightclaim.domain.AdjustmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AdjustmentRequest(
        @NotNull Long entryId,
        @NotNull AdjustmentType type,
        @NotNull @Positive BigDecimal amount,
        String reason) {
}
