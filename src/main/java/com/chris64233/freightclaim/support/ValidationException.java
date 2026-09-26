package com.chris64233.freightclaim.support;

/** 请求参数或金额分摊规则校验失败。映射为 HTTP 422。 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
