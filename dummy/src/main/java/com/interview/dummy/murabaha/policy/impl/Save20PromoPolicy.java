package com.interview.dummy.murabaha.policy.impl;

import com.interview.dummy.murabaha.domain.policy.PromoCodePolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * SAVE20 reduces the margin by 20% of its value (i.e. multiplies by 0.80).
 * The floor is enforced by {@code MarginResolver}, not here.
 */
@Component
public class Save20PromoPolicy implements PromoCodePolicy {

    private static final String CODE = "SAVE20";
    private static final BigDecimal FACTOR = new BigDecimal("0.80");

    @Override
    public boolean supports(String promoCode) {
        return CODE.equalsIgnoreCase(promoCode);
    }

    @Override
    public BigDecimal apply(BigDecimal baseMarginPercent) {
        return baseMarginPercent.multiply(FACTOR, MathContext.DECIMAL64);
    }
}
