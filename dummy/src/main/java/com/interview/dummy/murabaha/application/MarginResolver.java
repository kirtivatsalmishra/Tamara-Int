package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.api.error.InvalidPromoCodeException;
import com.interview.dummy.murabaha.domain.model.CommodityCategory;
import com.interview.dummy.murabaha.domain.policy.CommodityMarginPolicy;
import com.interview.dummy.murabaha.domain.policy.PromoCodePolicy;
import com.interview.dummy.murabaha.infrastructure.config.MurabahaProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resolves the applied margin percent given a commodity category and an
 * optional promo code. The policy lists are populated by Spring's collection
 * injection; new commodities or promos require zero edits here.
 */
@Component
public class MarginResolver {

    private final List<CommodityMarginPolicy> commodityPolicies;
    private final List<PromoCodePolicy> promoPolicies;
    private final BigDecimal defaultMargin;
    private final BigDecimal floor;

    public MarginResolver(List<CommodityMarginPolicy> commodityPolicies,
                          List<PromoCodePolicy> promoPolicies,
                          MurabahaProperties properties) {
        this.commodityPolicies = commodityPolicies;
        this.promoPolicies = promoPolicies;
        this.defaultMargin = properties.getDefaultMarginPercent();
        this.floor = properties.getMinMarginPercent();
    }

    /**
     * @param category   normalised commodity category
     * @param promoCode  optional; {@code null} or blank means "no promo"
     * @return the applied margin percent, never below the configured floor
     * @throws InvalidPromoCodeException when a non-blank promo code matches no policy
     */
    public BigDecimal resolve(CommodityCategory category, String promoCode) {
        BigDecimal base = baseMarginFor(category);
        BigDecimal adjusted = (promoCode == null || promoCode.isBlank())
                ? base
                : applyPromo(base, promoCode);
        return adjusted.max(floor);
    }

    private BigDecimal baseMarginFor(CommodityCategory category) {
        return commodityPolicies.stream()
                .filter(p -> p.supports(category))
                .findFirst()
                .map(CommodityMarginPolicy::baseMarginPercent)
                .orElse(defaultMargin);
    }

    private BigDecimal applyPromo(BigDecimal base, String promoCode) {
        return promoPolicies.stream()
                .filter(p -> p.supports(promoCode))
                .findFirst()
                .orElseThrow(() -> new InvalidPromoCodeException(promoCode))
                .apply(base);
    }
}
