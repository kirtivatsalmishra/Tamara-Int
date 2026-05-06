package com.interview.dummy.murabaha.policy.impl;

import com.interview.dummy.murabaha.domain.model.CommodityCategory;
import com.interview.dummy.murabaha.domain.policy.CommodityMarginPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CommodityMarginPolicyTest {

    @Test
    void goldClaimsGoldOnly() {
        CommodityMarginPolicy gold = new GoldMarginPolicy();
        assertThat(gold.supports(CommodityCategory.of("gold"))).isTrue();
        assertThat(gold.supports(CommodityCategory.of("oil"))).isFalse();
        assertThat(gold.baseMarginPercent()).isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    void oilClaimsOilOnly() {
        CommodityMarginPolicy p = new OilMarginPolicy();
        assertThat(p.supports(CommodityCategory.of("oil"))).isTrue();
        assertThat(p.supports(CommodityCategory.of("gold"))).isFalse();
        assertThat(p.baseMarginPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void metalsClaimsMetalsOnly() {
        CommodityMarginPolicy p = new MetalsMarginPolicy();
        assertThat(p.supports(CommodityCategory.of("metals"))).isTrue();
        assertThat(p.supports(CommodityCategory.of("wheat"))).isFalse();
        assertThat(p.baseMarginPercent()).isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void wheatClaimsWheatOnly() {
        CommodityMarginPolicy p = new WheatMarginPolicy();
        assertThat(p.supports(CommodityCategory.of("wheat"))).isTrue();
        assertThat(p.supports(CommodityCategory.of("metals"))).isFalse();
        assertThat(p.baseMarginPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
    }
}
