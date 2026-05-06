package com.interview.dummy.murabaha.domain.policy;

import com.interview.dummy.murabaha.domain.model.CommodityCategory;

import java.math.BigDecimal;

/**
 * One bean per commodity. Adding a new commodity is a new {@code @Component}
 * implementing this interface; {@code MarginResolver} discovers it through
 * collection injection. Edits to existing classes are not required (OCP).
 */
public interface CommodityMarginPolicy {

    /** True when this policy claims the supplied category. */
    boolean supports(CommodityCategory category);

    /** Base margin percent (e.g. {@code new BigDecimal("8.00")} for gold). */
    BigDecimal baseMarginPercent();
}
