package com.chris64233.freightclaim.support;

/** 业务前置条件不满足：活动索赔重复、并发竞争、证据版本过期、结算后修改等。映射为 HTTP 409。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
