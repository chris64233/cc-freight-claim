package com.chris64233.freightclaim.domain;

/** 索赔状态。 */
public enum ClaimStatus {
    /** 活动：可修改、可出责任决定 */
    ACTIVE,
    /** 已关闭（撤销/结案）：只读，不再允许新的责任决定 */
    CLOSED
}
