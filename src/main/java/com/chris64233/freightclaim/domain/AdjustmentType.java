package com.chris64233.freightclaim.domain;

/** 结算后追加调整的类型。 */
public enum AdjustmentType {
    /** 追偿：向责任承运段追回的款项，不设上限。 */
    RECOVERY,
    /** 冲回：退还/冲减已结算责任，累计不得超过对应已结算责任金额。 */
    REVERSAL
}
