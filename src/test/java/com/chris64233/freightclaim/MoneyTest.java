package com.chris64233.freightclaim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.freightclaim.support.Money;
import com.chris64233.freightclaim.support.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void allocationSumsExactlyToTotalEvenWhenDivisionHasRemainder() {
        // 100 按三等分：33.33 + 33.33 + 33.34，尾差由最后一段承担。
        List<BigDecimal> parts = Money.allocateByWeights(new BigDecimal("100.00"),
                List.of(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1")));

        assertThat(parts).containsExactly(new BigDecimal("33.33"),
                new BigDecimal("33.33"), new BigDecimal("33.34"));
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void allocationHonoursWeightsAndKeepsUnifiedScale() {
        List<BigDecimal> parts = Money.allocateByWeights(new BigDecimal("80.00"),
                List.of(new BigDecimal("1"), new BigDecimal("3")));

        assertThat(parts).containsExactly(new BigDecimal("20.00"), new BigDecimal("60.00"));
        parts.forEach(p -> assertThat(p.scale()).isEqualTo(Money.SCALE));
    }

    @Test
    void nonDivisibleAmountPutsRoundingDifferenceOnLastSegment() {
        // 10.00 按 1:1:1 -> 3.33, 3.33, 3.34
        List<BigDecimal> parts = Money.allocateByWeights(new BigDecimal("10.00"),
                List.of(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1")));
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void rejectsZeroWeightSumAndInvalidAmounts() {
        assertThatThrownBy(() -> Money.allocateByWeights(new BigDecimal("10.00"),
                List.of(new BigDecimal("0"))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("-1"), "x"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("1.999"), "x"))
                .isInstanceOf(ValidationException.class);
    }
}
