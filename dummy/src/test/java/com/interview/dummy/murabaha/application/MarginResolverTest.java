package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.api.error.InvalidPromoCodeException;
import com.interview.dummy.murabaha.domain.model.CommodityCategory;
import com.interview.dummy.murabaha.domain.policy.CommodityMarginPolicy;
import com.interview.dummy.murabaha.domain.policy.PromoCodePolicy;
import com.interview.dummy.murabaha.infrastructure.config.MurabahaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarginResolverTest {

    private MarginResolver resolver;

    @BeforeEach
    void setUp() {
        MurabahaProperties properties = new MurabahaProperties();
        // Use the real concrete commodity & promo policies to anchor the spec table here.
        List<CommodityMarginPolicy> commodities = List.of(
                fixed("gold", "8.00"),
                fixed("oil", "10.00"),
                fixed("metals", "12.00"),
                fixed("wheat", "15.00")
        );
        List<PromoCodePolicy> promos = List.of(
                promo("SAVE10", new BigDecimal("0.90")),
                promo("SAVE20", new BigDecimal("0.80"))
        );
        resolver = new MarginResolver(commodities, promos, properties);
    }

    @Test
    void goldNoPromo() {
        assertThat(resolver.resolve(CommodityCategory.of("gold"), null))
                .isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    void oilNoPromo() {
        assertThat(resolver.resolve(CommodityCategory.of("oil"), null))
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void metalsNoPromo() {
        assertThat(resolver.resolve(CommodityCategory.of("metals"), null))
                .isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void wheatNoPromo() {
        assertThat(resolver.resolve(CommodityCategory.of("wheat"), null))
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void unknownCategoryDefaultsTo12() {
        assertThat(resolver.resolve(CommodityCategory.of("electronics"), null))
                .isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void save10OnGoldYields7Point2() {
        // 8.00 * 0.90 = 7.20, above 5% floor
        assertThat(resolver.resolve(CommodityCategory.of("gold"), "SAVE10"))
                .isEqualByComparingTo(new BigDecimal("7.20"));
    }

    @Test
    void save20OnWheatYields12() {
        // 15.00 * 0.80 = 12.00, above floor
        assertThat(resolver.resolve(CommodityCategory.of("wheat"), "SAVE20"))
                .isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void floorAppliedWhenPromoWouldDropBelowFive() {
        // base 5.00 (forced) * 0.80 = 4.00 -> clamped to 5.00
        MarginResolver lowResolver = new MarginResolver(
                List.of(fixed("widget", "5.00")),
                List.of(promo("SAVE20", new BigDecimal("0.80"))),
                new MurabahaProperties());
        assertThat(lowResolver.resolve(CommodityCategory.of("widget"), "SAVE20"))
                .isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void unknownPromoThrows() {
        assertThatThrownBy(() -> resolver.resolve(CommodityCategory.of("gold"), "BOGUS"))
                .isInstanceOf(InvalidPromoCodeException.class);
    }

    @Test
    void blankPromoIsIgnored() {
        assertThat(resolver.resolve(CommodityCategory.of("gold"), "  "))
                .isEqualByComparingTo(new BigDecimal("8.00"));
    }

    private static CommodityMarginPolicy fixed(String code, String margin) {
        return new CommodityMarginPolicy() {
            @Override public boolean supports(CommodityCategory category) { return code.equals(category.code()); }
            @Override public BigDecimal baseMarginPercent() { return new BigDecimal(margin); }
        };
    }

    private static PromoCodePolicy promo(String code, BigDecimal factor) {
        return new PromoCodePolicy() {
            @Override public boolean supports(String promoCode) { return code.equalsIgnoreCase(promoCode); }
            @Override public BigDecimal apply(BigDecimal base) {
                return base.multiply(factor, java.math.MathContext.DECIMAL64);
            }
        };
    }
}
