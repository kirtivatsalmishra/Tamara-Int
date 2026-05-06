package com.interview.dummy.murabaha.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Wire representation of a {@code RepaymentPlan}. */
public record RepaymentPlanResponse(
        UUID planId,
        String customerId,
        BigDecimal commodityCost,
        String currencyCode,
        BigDecimal appliedMarginPercent,
        BigDecimal totalProfit,
        BigDecimal totalPayable,
        BigDecimal baseInstallmentAmount,
        List<InstallmentDto> schedule,
        String contractSummary
) { }
