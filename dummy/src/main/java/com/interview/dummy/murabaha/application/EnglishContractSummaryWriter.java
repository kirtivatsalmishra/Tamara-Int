package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import org.springframework.stereotype.Component;

/**
 * English contract summary matching the spec template verbatim. The currency
 * code is taken from the plan, never hard-coded.
 */
@Component
public class EnglishContractSummaryWriter implements ContractSummaryWriter {

    @Override
    public String write(RepaymentPlan plan) {
        String currency = plan.commodityCost().currency().getCurrencyCode();
        return String.format(
                "This Murabaha contract confirms our purchase of the commodity on your behalf for %s %s. "
                        + "We are selling it to you at a total price of %s %s, "
                        + "which includes our profit of %s %s. "
                        + "This amount is to be paid in %d equal monthly installments of %s %s.",
                currency, plan.commodityCost().amount().toPlainString(),
                currency, plan.totalPayable().amount().toPlainString(),
                currency, plan.totalProfit().amount().toPlainString(),
                plan.schedule().size(),
                currency, plan.baseInstallmentAmount().amount().toPlainString());
    }
}
