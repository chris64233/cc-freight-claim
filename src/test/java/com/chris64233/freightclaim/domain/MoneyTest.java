package com.chris64233.freightclaim.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class MoneyTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void allocateSumsExactlyToTotalEvenWhenRoundingWouldLoseCents() {
        // 100 / 3：四舍五入各份之和可能是 99.99 或 100.02，最大余额法必须严格等于 100.00
        List<BigDecimal> parts = Money.allocate(bd("100.00"), List.of(bd("1"), bd("1"), bd("1")));

        assertThat(parts).hasSize(3);
        assertThat(parts).allSatisfy(p -> assertThat(p.scale()).isEqualTo(2));
        BigDecimal sum = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
        // 每份都是 2 位小数，且差值至多 1 分
        assertThat(parts).containsExactlyInAnyOrder(
                bd("33.34"), bd("33.33"), bd("33.33"));
    }

    @Test
    void allocateUnevenWeightsRespectsProportionAndSum() {
        List<BigDecimal> parts = Money.allocate(bd("500.00"),
                List.of(bd("2"), bd("3"), bd("5")));

        BigDecimal sum = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("500.00");
        // 20% / 30% / 50%
        assertThat(parts).containsExactly(bd("100.00"), bd("150.00"), bd("250.00"));
    }

    @Test
    void allocateHandlesAmountThatDoesNotDivideEvenlyWithManySegments() {
        List<BigDecimal> weights = java.util.stream.Stream.generate(() -> bd("1"))
                .limit(7).toList();
        List<BigDecimal> parts = Money.allocate(bd("0.10"), weights);

        BigDecimal sum = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0.10");
        // 每份 0.014285...：先各分 0.01（共 0.07），余 3 分补给 3 个承运段
        assertThat(parts).filteredOn(p -> p.compareTo(bd("0.02")) == 0).hasSize(3);
        assertThat(parts).filteredOn(p -> p.compareTo(bd("0.01")) == 0).hasSize(4);
    }

    @Test
    void allocateZeroTotalGivesAllZero() {
        List<BigDecimal> parts = Money.allocate(bd("0.00"), List.of(bd("1"), bd("2")));
        assertThat(parts).containsExactly(bd("0.00"), bd("0.00"));
    }

    @Test
    void allocateRejectsNegativeTotalAndBadWeights() {
        assertThatThrownBy(() -> Money.allocate(bd("-1"), List.of(bd("1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.allocate(bd("10"), List.of(bd("0"), bd("0"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.allocate(bd("10"), List.of(bd("-1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofAppliesUnifiedRoundingHalfUp() {
        assertThat(Money.of(bd("1.005"))).isEqualByComparingTo("1.01");
        assertThat(Money.of(bd("1.004"))).isEqualByComparingTo("1.00");
    }
}
