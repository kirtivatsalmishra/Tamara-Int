package com.interview.dummy.murabaha.domain.model;

import com.interview.dummy.murabaha.api.error.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallmentTest {

    private static final Currency SAR = Currency.getInstance("SAR");

    @Test
    void rejectsZeroSequence() {
        assertThatThrownBy(() -> new Installment(0, LocalDate.now(),
                Money.of(new BigDecimal("10.00"), SAR)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> new Installment(1, LocalDate.now(), Money.zero(SAR)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Installment(1, LocalDate.now(),
                Money.of(new BigDecimal("-1.00"), SAR)))
                .isInstanceOf(DomainException.class);
    }
}
