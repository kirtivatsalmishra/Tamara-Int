package com.interview.dummy.murabaha.domain.policy;

import java.math.BigDecimal;

/**
 * One bean per promo. {@link #apply(BigDecimal)} adjusts the base margin; the
 * caller is responsible for enforcing the {@code minMarginPercent} floor.
 */
public interface PromoCodePolicy {

    /** True when this policy claims the supplied promo code (case-insensitive comparison expected). */
    boolean supports(String promoCode);

    /** Returns the adjusted margin. The floor is applied by the caller. */
    BigDecimal apply(BigDecimal baseMarginPercent);
}
