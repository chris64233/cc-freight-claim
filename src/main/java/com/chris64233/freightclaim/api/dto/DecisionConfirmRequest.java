package com.chris64233.freightclaim.api.dto;

/**
 * 确认责任决定。
 * 可携带客户端看到的版本，服务端会再次以数据库最新版本校验；过期返回 409。
 */
public record DecisionConfirmRequest(
        Long expectedContentVersion,
        Long expectedEvidenceVersion) {
}
