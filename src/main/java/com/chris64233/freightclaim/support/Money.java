package com.chris64233.freightclaim.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 全系统统一的金额规则：币种无关，保留 2 位小数，四舍五入（HALF_UP）。
 */
public final class Money {

    /** 金额统一精度（分）。 */
    public static final int SCALE = 2;
    /** 统一舍入规则。 */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    /** 0 元。 */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    /** 归一化到统一精度。 */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(SCALE, ROUNDING);
    }

    /** 校验金额非空、为正数且小数位不超过 2 位（超出精度的输入一律拒绝，避免隐式吞掉金额）。 */
    public static BigDecimal requirePositive(BigDecimal amount, String field) {
        if (amount == null) {
            throw new ValidationException(field + "不能为空");
        }
        if (amount.signum() <= 0) {
            throw new ValidationException(field + "必须大于 0");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new ValidationException(field + "小数位不能超过 " + SCALE + " 位");
        }
        return amount.setScale(SCALE, ROUNDING);
    }

    /** 非负校验（用于冲回等可以为 0 的场景外的入口）。 */
    public static BigDecimal requireNonNegative(BigDecimal amount, String field) {
        if (amount == null) {
            throw new ValidationException(field + "不能为空");
        }
        if (amount.signum() < 0) {
            throw new ValidationException(field + "不能为负");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new ValidationException(field + "小数位不能超过 " + SCALE + " 位");
        }
        return amount.setScale(SCALE, ROUNDING);
    }

    /**
     * 按权重把总额按比例分摊到多个承运段。
     * 先按权重四舍五入分摊，最后一段承担尾差，保证各分录金额之和<strong>恰好</strong>等于总额。
     *
     * @param total   待分摊总额（已归一化）
     * @param weights 每段的权重（必须全部为正）
     * @return 与 weights 等长、等序的分摊金额
     */
    public static List<BigDecimal> allocateByWeights(BigDecimal total, List<BigDecimal> weights) {
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.signum() <= 0) {
            throw new ValidationException("分摊权重之和必须大于 0");
        }
        BigDecimal[] allocated = new BigDecimal[weights.size()];
        BigDecimal distributed = ZERO;
        for (int i = 0; i < weights.size(); i++) {
            if (i == weights.size() - 1) {
                allocated[i] = total.subtract(distributed);
            } else {
                BigDecimal part = total.multiply(weights.get(i))
                        .divide(weightSum, SCALE, ROUNDING);
                allocated[i] = part;
                distributed = distributed.add(part);
            }
        }
        return List.of(allocated);
    }
}
