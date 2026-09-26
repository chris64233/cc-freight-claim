package com.chris64233.freightclaim.web.request;

import com.chris64233.freightclaim.domain.LossType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 索赔内容变更请求（内容版本自动加 1）。 */
public record ClaimReviseRequest(
        @NotNull @Positive BigDecimal lossAmount,
        @NotNull LossType lossType,
        @NotBlank String evidence) {
}
