package com.chris64233.freightclaim.domain;

/** 责任决定状态。 */
public enum DecisionStatus {
    /** 草拟：可反复修改分摊方案，不产生责任分录。 */
    DRAFT,
    /** 已确认：一次性生成全部责任分录，结算前可基于新版本重做；结算后不可变。 */
    CONFIRMED
}
