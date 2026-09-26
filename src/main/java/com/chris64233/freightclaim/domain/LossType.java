package com.chris64233.freightclaim.domain;

/** 损失类型。 */
public enum LossType {
    /** 货损。 */
    DAMAGE,
    /** 货差/短少。 */
    SHORTAGE,
    /** 灭失。 */
    LOSS,
    /** 污染。 */
    CONTAMINATION,
    /** 延误导致的损失。 */
    DELAY
}
