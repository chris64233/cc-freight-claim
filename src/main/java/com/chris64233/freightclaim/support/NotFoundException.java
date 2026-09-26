package com.chris64233.freightclaim.support;

/** 请求引用的资源不存在。 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
