package com.chris64233.freightclaim.domain;

/** 结算后的追加调整类型。 */
public enum AdjustmentType {
    /** 追偿：向责任承运段追加追回 */
    RECOVERY,
    /** 冲回：冲减已结算责任，金额不得超过对应已结算责任 */
    REVERSAL
}
