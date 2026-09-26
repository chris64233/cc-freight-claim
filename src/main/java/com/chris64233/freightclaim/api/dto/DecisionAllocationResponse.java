package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;

public record DecisionAllocationResponse(
        Integer segmentSeq,
        String carrierCode,
        BigDecimal ratioWeight,
        /** 已确认决定返回按比例计算出的分摊金额；草稿尚无金额为 null。 */
        BigDecimal allocatedAmount) {
}
