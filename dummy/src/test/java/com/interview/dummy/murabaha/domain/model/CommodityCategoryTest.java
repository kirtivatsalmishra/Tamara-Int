package com.interview.dummy.murabaha.domain.model;

import com.interview.dummy.murabaha.api.error.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommodityCategoryTest {

    @Test
    void normalisesCase() {
        assertThat(CommodityCategory.of("Gold").code()).isEqualTo("gold");
    }

    @Test
    void trimsWhitespace() {
        assertThat(CommodityCategory.of("  oil  ").code()).isEqualTo("oil");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> CommodityCategory.of("   "))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void equalsByNormalisedCode() {
        assertThat(CommodityCategory.of("Gold")).isEqualTo(CommodityCategory.of("gold"));
    }
}
