package com.chris64233.freightclaim.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 单条责任分摊：承运段 + 权重（正数即可，服务端归一化）。 */
public record AllocationRequest(
        @NotNull Long segmentId,
        @NotNull @Positive BigDecimal weight) {
}
