package com.chris64233.freightclaim.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/**
 * 责任决定请求（草拟/确认共用）。
 *
 * @param expectedEvidenceVersion 可选，客户端持有的交接证据版本，过期返回 409
 * @param expectedClaimVersion    可选，客户端持有的索赔内容版本，过期返回 409
 */
public record DecisionRequest(
        @NotNull @Positive BigDecimal approvedAmount,
        @NotEmpty List<@Valid AllocationRequest> allocations,
        Integer expectedEvidenceVersion,
        Integer expectedClaimVersion) {
}
