package com.interview.dummy.murabaha.domain.model;

import com.interview.dummy.murabaha.api.error.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency SAR = Currency.getInstance("SAR");
    private static final Currency KWD = Currency.getInstance("KWD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test
    void normalisesScaleForSar() {
        Money money = Money.of(new BigDecimal("100"), SAR);
        assertThat(money.amount().scale()).isEqualTo(2);
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void normalisesScaleForKwd() {
        Money money = Money.of(new BigDecimal("10"), KWD);
        assertThat(money.amount().scale()).isEqualTo(3);
    }

    @Test
    void normalisesScaleForJpy() {
        Money money = Money.of(new BigDecimal("250.49"), JPY);
        assertThat(money.amount().scale()).isEqualTo(0);
        // HALF_EVEN: .49 rounds down to 0
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("250"));
    }

    @Test
    void addsSameCurrency() {
        Money a = Money.of(new BigDecimal("100.00"), SAR);
        Money b = Money.of(new BigDecimal("0.55"), SAR);
        assertThat(a.add(b).amount()).isEqualByComparingTo(new BigDecimal("100.55"));
    }

    @Test
    void rejectsCurrencyMismatchOnAdd() {
        Money a = Money.of(new BigDecimal("100.00"), SAR);
        Money b = Money.of(new BigDecimal("100.00"), KWD);
        assertThatThrownBy(() -> a.add(b)).isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsCurrencyMismatchOnSubtract() {
        Money a = Money.of(new BigDecimal("100.00"), SAR);
        Money b = Money.of(new BigDecimal("100.00"), KWD);
        assertThatThrownBy(() -> a.subtract(b)).isInstanceOf(DomainException.class);
    }

    @Test
    void zeroFactoryProducesScaledZero() {
        Money zero = Money.zero(SAR);
        assertThat(zero.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(zero.amount().scale()).isEqualTo(2);
        assertThat(zero.isPositive()).isFalse();
    }
}
