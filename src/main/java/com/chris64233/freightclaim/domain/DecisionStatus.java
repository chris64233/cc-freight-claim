package com.chris64233.freightclaim.domain;

/** 责任决定状态。 */
public enum DecisionStatus {
    /** 草稿：尚未确认，可更新分摊方案 */
    DRAFT,
    /** 已确认：全部责任分录一次性生成，不可再修改 */
    CONFIRMED
}
