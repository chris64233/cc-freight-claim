package com.chris64233.freightclaim.web.view;

import java.math.BigDecimal;

/** 单条责任分摊分录视图，含结算后追偿/冲回累计与净额。 */
public record EntryView(
        Long id,
        Long segmentId,
        int segmentSequenceNo,
        String carrierCode,
        String carrierName,
        BigDecimal shareRatio,
        BigDecimal allocatedAmount,
        BigDecimal totalRecovery,
        BigDecimal totalReversal,
        /** 净责任 = 分摊金额 + 累计追偿 - 累计冲回。 */
        BigDecimal netLiability,
        boolean settled) {
}
