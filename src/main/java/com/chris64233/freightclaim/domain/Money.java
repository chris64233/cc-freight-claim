package com.chris64233.freightclaim.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 金额工具：系统内统一使用 {@link BigDecimal}，金额一律保留 2 位小数，
 * 统一采用 {@link RoundingMode#HALF_UP} 四舍五入。
 */
public final class Money {

    /** 金额精度（分）。 */
    public static final int SCALE = 2;

    /** 统一舍入规则。 */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {
    }

    /** 按统一精度与舍入规则规整金额。 */
    public static BigDecimal of(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        return amount.setScale(SCALE, ROUNDING);
    }

    /** 判断金额为正。 */
    public static boolean positive(BigDecimal amount) {
        return of(amount).signum() > 0;
    }

    /** 判断金额非负。 */
    public static boolean nonNegative(BigDecimal amount) {
        return of(amount).signum() >= 0;
    }

    /**
     * 按权重把总额比例分摊到若干份，采用“最大余额法”保证：
     * 每一份都是 2 位小数、全部为正或零（取决于权重），且各份之和严格等于总额，
     * 不存在四舍五入造成的一分钱误差。余数按金额从大到小（再按下标）补给/扣减。
     *
     * @param total   待分摊总额（非负）
     * @param weights 每份权重（必须全部为非负，且总和为正）
     * @return 与权重等长的分摊结果，顺序与权重一致
     */
    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        BigDecimal normalizedTotal = of(total);
        if (normalizedTotal.signum() < 0) {
            throw new IllegalArgumentException("分摊总额不能为负");
        }
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("分摊权重不能为空");
        }
        BigDecimal weightSum = BigDecimal.ZERO;
        for (BigDecimal w : weights) {
            if (w == null || w.signum() < 0) {
                throw new IllegalArgumentException("分摊权重必须非负");
            }
            weightSum = weightSum.add(w);
        }
        if (weightSum.signum() <= 0) {
            throw new IllegalArgumentException("分摊权重之和必须为正");
        }

        // 精确份额
        int n = weights.size();
        BigDecimal[] exact = new BigDecimal[n];
        BigDecimal totalExact = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            exact[i] = normalizedTotal.multiply(weights.get(i))
                    .divide(weightSum, 10, RoundingMode.DOWN);
            totalExact = totalExact.add(exact[i]);
        }
        // 若权重没有完全覆盖（理论上不会，兜底处理），把剩余并到第一个权重
        if (totalExact.compareTo(normalizedTotal) < 0) {
            exact[0] = exact[0].add(normalizedTotal.subtract(totalExact));
        }

        // 先向下取整到分，保证不超过精确份额（不会产生负金额）
        BigDecimal unit = BigDecimal.ONE.movePointLeft(SCALE); // 0.01
        BigDecimal[] floored = new BigDecimal[n];
        BigDecimal[] remainder = new BigDecimal[n];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            floored[i] = exact[i].setScale(SCALE, RoundingMode.DOWN);
            remainder[i] = exact[i].subtract(floored[i]);
            allocated = allocated.add(floored[i]);
        }
        long centsLeft = normalizedTotal.subtract(allocated)
                .movePointRight(SCALE).setScale(0, RoundingMode.HALF_UP).longValueExact();

        // 按余数从大到小排序，相同余数按金额（精确份额）大的优先，再按下标，保证稳定可预期
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort((a, b) -> {
            int cmp = remainder[b].compareTo(remainder[a]);
            if (cmp != 0) {
                return cmp;
            }
            cmp = exact[b].compareTo(exact[a]);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a, b);
        });

        BigDecimal[] result = floored.clone();
        for (int k = 0; k < centsLeft; k++) {
            int idx = order.get(k % n);
            result[idx] = result[idx].add(unit);
        }

        List<BigDecimal> list = new ArrayList<>(n);
        for (BigDecimal v : result) {
            list.add(of(v));
        }

        // 最终校验：和必须严格等于总额
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : list) {
            sum = sum.add(v);
        }
        if (sum.compareTo(normalizedTotal) != 0) {
            throw new IllegalStateException("分摊结果之和不等于总额: " + sum + " != " + normalizedTotal);
        }
        return list;
    }
}
