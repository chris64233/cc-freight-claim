package com.chris64233.freightclaim.service;

import java.math.BigDecimal;

/**
 * 责任分摊入参：承运段 ID + 分摊权重（权重为正即可，服务端按权重归一化计算占比与金额）。
 */
public record AllocationItem(Long segmentId, BigDecimal weight) {
}
