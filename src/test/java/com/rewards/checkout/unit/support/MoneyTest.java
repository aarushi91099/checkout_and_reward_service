package com.rewards.checkout.unit.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.rewards.checkout.support.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizeRoundsHalfUpToTwoDecimalPlaces() {
        assertThat(Money.normalize(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(Money.normalize(new BigDecimal("1.004"))).isEqualByComparingTo("1.00");
    }

    @Test
    void lineTotalMultipliesUnitPriceByQuantityAndNormalizes() {
        assertThat(Money.lineTotal(new BigDecimal("9.999"), 3)).isEqualByComparingTo("30.00");
    }

    @Test
    void discountForAppliesPercentageOfSubtotal() {
        assertThat(Money.discountFor(new BigDecimal("100.00"), new BigDecimal("10"))).isEqualByComparingTo("10.00");
    }

    @Test
    void totalAfterDiscountSubtractsDiscountFromSubtotal() {
        assertThat(Money.totalAfterDiscount(new BigDecimal("100.00"), new BigDecimal("10.00"))).isEqualByComparingTo("90.00");
    }

    @Test
    void totalAfterDiscountNeverGoesNegative() {
        assertThat(Money.totalAfterDiscount(new BigDecimal("10.00"), new BigDecimal("50.00"))).isEqualByComparingTo("0.00");
    }
}
