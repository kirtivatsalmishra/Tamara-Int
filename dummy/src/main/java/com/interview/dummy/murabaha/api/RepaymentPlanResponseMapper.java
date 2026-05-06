package com.interview.dummy.murabaha.api;

import com.interview.dummy.murabaha.api.dto.InstallmentDto;
import com.interview.dummy.murabaha.api.dto.RepaymentPlanResponse;
import com.interview.dummy.murabaha.application.RepaymentPlanService.PlanWithSummary;
import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import org.springframework.stereotype.Component;

import java.util.List;

/** Translates the domain aggregate (plus its summary) into the wire response. */
@Component
public class RepaymentPlanResponseMapper {

    public RepaymentPlanResponse toResponse(PlanWithSummary planWithSummary) {
        RepaymentPlan plan = planWithSummary.plan();
        List<InstallmentDto> schedule = plan.schedule().stream()
                .map(i -> new InstallmentDto(i.sequence(), i.dueDate(), i.amount().amount()))
                .toList();
        return new RepaymentPlanResponse(
                plan.id(),
                plan.customerId(),
                plan.commodityCost().amount(),
                plan.commodityCost().currency().getCurrencyCode(),
                plan.appliedMarginPercent(),
                plan.totalProfit().amount(),
                plan.totalPayable().amount(),
                plan.baseInstallmentAmount().amount(),
                schedule,
                planWithSummary.contractSummary());
    }
}
