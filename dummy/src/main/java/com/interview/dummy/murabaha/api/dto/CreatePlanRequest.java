package com.interview.dummy.murabaha.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * API request to create a Murabaha repayment plan.
 *
 * <p>{@code currencyCode} is optional; when omitted the configured
 * {@code murabaha.currency-code} default applies (SAR).
 *
 * <p>{@code purchaseDate} is optional; when omitted today's business-zone date
 * is used.
 */
public record CreatePlanRequest(
        @NotBlank String customerId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal commodityCost,
        @NotBlank String commodityCategory,
        @NotNull @Min(2) @Max(12) Integer installments,
        String promoCode,
        LocalDate purchaseDate,
        String currencyCode
) { }
