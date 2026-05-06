package com.interview.dummy.murabaha.policy.impl;

import com.interview.dummy.murabaha.domain.policy.PromoCodePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PromoCodePolicyTest {

    @Test
    void save10MultipliesByPoint90AndIsCaseInsensitive() {
        PromoCodePolicy p = new Save10PromoPolicy();
        assertThat(p.supports("SAVE10")).isTrue();
        assertThat(p.supports("save10")).isTrue();
        assertThat(p.supports("SAVE20")).isFalse();
        assertThat(p.apply(new BigDecimal("10.00"))).isEqualByComparingTo(new BigDecimal("9.00"));
    }

    @Test
    void save20MultipliesByPoint80AndIsCaseInsensitive() {
        PromoCodePolicy p = new Save20PromoPolicy();
        assertThat(p.supports("SAVE20")).isTrue();
        assertThat(p.supports("Save20")).isTrue();
        assertThat(p.supports("SAVE10")).isFalse();
        assertThat(p.apply(new BigDecimal("10.00"))).isEqualByComparingTo(new BigDecimal("8.00"));
    }
}
