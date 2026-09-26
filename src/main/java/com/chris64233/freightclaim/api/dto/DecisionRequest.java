package com.chris64233.freightclaim.api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 创建责任决定草稿。
 * approvedAmount 为认可金额；allocations 给出各承运段的分摊权重（比例）。
 */
public record DecisionRequest(
        @NotNull @PositiveOrZero BigDecimal approvedAmount,
        String remark,
        @NotEmpty @Valid List<AllocationRequest> allocations) {

    /** 按承运段顺序号指定分摊权重。 */
    public record AllocationRequest(
            @NotNull Integer segmentSeq,
            @NotNull @Positive BigDecimal ratioWeight) {
    }
}
