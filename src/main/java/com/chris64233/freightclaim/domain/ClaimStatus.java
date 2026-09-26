package com.chris64233.freightclaim.domain;

/** 索赔状态。 */
public enum ClaimStatus {
    /** 活动中：可登记责任决定、结算。 */
    ACTIVE,
    /** 已关闭：在确认决定前由业务人员关闭（例如撤回、协商撤销）。 */
    CLOSED
}
