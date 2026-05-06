package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.domain.model.RepaymentPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Stable fingerprint of the input that produced a {@link RepaymentPlan}.
 *
 * <p>Used to decide whether a replayed {@code Idempotency-Key} is a true replay
 * (same input → return original plan) or a conflict (different input → 409).
 *
 * <p>We compare against the produced plan rather than the raw request so that
 * equivalent inputs (e.g. both omitting {@code purchaseDate} on the same day)
 * are treated as the same submission.
 */
public record PlanRequestFingerprint(
        String customerId,
        BigDecimal commodityCost,
        String currencyCode,
        String commodityCategory,
        int installments,
        String promoCode,
        LocalDate purchaseDate
) {

    public static PlanRequestFingerprint from(String customerId,
                                              BigDecimal commodityCost,
                                              String currencyCode,
                                              String commodityCategory,
                                              int installments,
                                              String promoCode,
                                              LocalDate purchaseDate) {
        return new PlanRequestFingerprint(
                customerId,
                commodityCost.stripTrailingZeros(),
                currencyCode == null ? null : currencyCode.toUpperCase(java.util.Locale.ROOT),
                commodityCategory == null ? null : commodityCategory.trim().toLowerCase(java.util.Locale.ROOT),
                installments,
                promoCode == null || promoCode.isBlank() ? null : promoCode.toUpperCase(java.util.Locale.ROOT),
                purchaseDate);
    }

    public boolean matches(PlanRequestFingerprint other) {
        if (other == null) return false;
        return Objects.equals(customerId, other.customerId)
                && commodityCost.compareTo(other.commodityCost) == 0
                && Objects.equals(currencyCode, other.currencyCode)
                && Objects.equals(commodityCategory, other.commodityCategory)
                && installments == other.installments
                && Objects.equals(promoCode, other.promoCode)
                && Objects.equals(purchaseDate, other.purchaseDate);
    }
}
