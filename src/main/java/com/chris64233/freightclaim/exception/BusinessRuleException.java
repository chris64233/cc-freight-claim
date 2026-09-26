package com.chris64233.freightclaim.exception;

/** 业务规则校验失败（400）。 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
