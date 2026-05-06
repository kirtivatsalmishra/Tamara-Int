package com.interview.dummy.murabaha.policy.impl;

import com.interview.dummy.murabaha.domain.model.CommodityCategory;
import com.interview.dummy.murabaha.domain.policy.CommodityMarginPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OilMarginPolicy implements CommodityMarginPolicy {

    private static final BigDecimal MARGIN = new BigDecimal("10.00");

    @Override
    public boolean supports(CommodityCategory category) {
        return "oil".equals(category.code());
    }

    @Override
    public BigDecimal baseMarginPercent() {
        return MARGIN;
    }
}
