package com.chris64233.freightclaim.service;

import com.chris64233.freightclaim.web.view.ClaimView;

/** 索赔登记结果：created=false 表示命中幂等、返回既有索赔。 */
public record ClaimRegistration(ClaimView claim, boolean created) {
}
