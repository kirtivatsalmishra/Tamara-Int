package com.interview.dummy.murabaha.infrastructure.money;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyScaleProviderTest {

    private final CurrencyScaleProvider provider = new CurrencyScaleProvider();

    @Test
    void sarHasScaleTwo() {
        assertThat(provider.scaleOf(Currency.getInstance("SAR"))).isEqualTo(2);
    }

    @Test
    void kwdHasScaleThree() {
        assertThat(provider.scaleOf(Currency.getInstance("KWD"))).isEqualTo(3);
    }

    @Test
    void jpyHasScaleZero() {
        assertThat(provider.scaleOf(Currency.getInstance("JPY"))).isEqualTo(0);
    }
}
