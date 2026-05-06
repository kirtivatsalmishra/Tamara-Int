package com.interview.dummy.murabaha.policy.impl;

import com.interview.dummy.murabaha.domain.policy.PromoCodePolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * SAVE10 reduces the margin by 10% of its value (i.e. multiplies by 0.90).
 * The floor is enforced by {@code MarginResolver}, not here.
 */
@Component
public class Save10PromoPolicy implements PromoCodePolicy {

    private static final String CODE = "SAVE10";
    private static final BigDecimal FACTOR = new BigDecimal("0.90");

    @Override
    public boolean supports(String promoCode) {
        return CODE.equalsIgnoreCase(promoCode);
    }

    @Override
    public BigDecimal apply(BigDecimal baseMarginPercent) {
        return baseMarginPercent.multiply(FACTOR, MathContext.DECIMAL64);
    }
}
