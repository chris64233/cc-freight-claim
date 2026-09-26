package com.chris64233.freightclaim.exception;

/**
 * 版本冲突（409）：责任决定所依据的索赔内容版本或运输单交接证据版本已过期，
 * 或存在并发唯一约束冲突（活动索赔重复 / 并发确认产生两套结果）。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
