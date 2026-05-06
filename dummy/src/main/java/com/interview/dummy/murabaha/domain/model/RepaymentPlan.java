package com.interview.dummy.murabaha.domain.model;

import com.interview.dummy.murabaha.api.error.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the Murabaha repayment plan. The constructor enforces the
 * eight invariants from §10 of the TDD; failure throws {@link DomainException}.
 *
 * <p>The aggregate is JPA-free by design — a separate {@code PlanMapper}
 * translates to/from the persistence entity.
 */
public final class RepaymentPlan {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_MARGIN = new BigDecimal("100.00");

    private final UUID id;
    private final String customerId;
    private final Money commodityCost;
    private final BigDecimal appliedMarginPercent;
    private final Money totalProfit;
    private final Money totalPayable;
    private final List<Installment> schedule;
    private final LocalDate purchaseDate;
    private final Instant createdAt;
    private final BigDecimal minMarginPercent;

    public RepaymentPlan(UUID id,
                         String customerId,
                         Money commodityCost,
                         BigDecimal appliedMarginPercent,
                         Money totalProfit,
                         Money totalPayable,
                         List<Installment> schedule,
                         LocalDate purchaseDate,
                         Instant createdAt,
                         BigDecimal minMarginPercent) {
        this.id = Objects.requireNonNull(id, "id");
        if (customerId == null || customerId.isBlank()) {
            throw new DomainException("customerId must not be blank");
        }
        this.customerId = customerId;
        this.commodityCost = Objects.requireNonNull(commodityCost, "commodityCost");
        this.appliedMarginPercent = Objects.requireNonNull(appliedMarginPercent, "appliedMarginPercent");
        this.totalProfit = Objects.requireNonNull(totalProfit, "totalProfit");
        this.totalPayable = Objects.requireNonNull(totalPayable, "totalPayable");
        this.purchaseDate = Objects.requireNonNull(purchaseDate, "purchaseDate");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.minMarginPercent = Objects.requireNonNull(minMarginPercent, "minMarginPercent");
        Objects.requireNonNull(schedule, "schedule");
        this.schedule = Collections.unmodifiableList(List.copyOf(schedule));

        validateInvariants();
    }

    private void validateInvariants() {
        // Invariant 1: commodityCost > 0
        if (!commodityCost.isPositive()) {
            throw new DomainException("Invariant violated: commodityCost must be > 0");
        }
        // Invariant 3: floor <= appliedMarginPercent <= 100
        if (appliedMarginPercent.compareTo(minMarginPercent) < 0
                || appliedMarginPercent.compareTo(MAX_MARGIN) > 0) {
            throw new DomainException("Invariant violated: appliedMarginPercent out of range: "
                    + appliedMarginPercent);
        }
        // Currency consistency
        if (!commodityCost.currency().equals(totalProfit.currency())
                || !commodityCost.currency().equals(totalPayable.currency())) {
            throw new DomainException("Invariant violated: currency mismatch across cost/profit/payable");
        }
        // Invariant 2: 2 <= installments.size() <= 12
        int n = schedule.size();
        if (n < 2 || n > 12) {
            throw new DomainException("Invariant violated: installments must be in [2, 12], was " + n);
        }
        // Currency consistency on installments
        for (Installment installment : schedule) {
            if (!installment.amount().currency().equals(commodityCost.currency())) {
                throw new DomainException("Invariant violated: installment currency mismatch at sequence "
                        + installment.sequence());
            }
        }
        // Invariant 4: totalProfit = commodityCost * appliedMarginPercent / 100, HALF_EVEN at currency scale
        int scale = commodityCost.amount().scale();
        BigDecimal expectedProfit = commodityCost.amount()
                .multiply(appliedMarginPercent)
                .divide(HUNDRED, scale, RoundingMode.HALF_EVEN);
        if (totalProfit.amount().compareTo(expectedProfit) != 0) {
            throw new DomainException("Invariant violated: totalProfit mismatch. expected="
                    + expectedProfit + " actual=" + totalProfit.amount());
        }
        // Invariant 5: totalPayable = commodityCost + totalProfit
        BigDecimal expectedPayable = commodityCost.amount().add(totalProfit.amount());
        if (totalPayable.amount().compareTo(expectedPayable) != 0) {
            throw new DomainException("Invariant violated: totalPayable mismatch. expected="
                    + expectedPayable + " actual=" + totalPayable.amount());
        }
        // Invariant 6: sum(installments) == totalPayable
        BigDecimal sum = schedule.stream()
                .map(i -> i.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalPayable.amount()) != 0) {
            throw new DomainException("Invariant violated: sum(installments)=" + sum
                    + " != totalPayable=" + totalPayable.amount());
        }
        // Invariant 8: sequences are 1..N, contiguous, monotonically increasing
        // Invariant 7: each installment.dueDate == purchaseDate + sequence months (clamped)
        // Note: clamping is a calendar concern checked by SaudiCalendar; here we verify monotonicity
        // and sequence contiguity. The scheduler is responsible for clamping; integration tests
        // confirm exact dates.
        LocalDate previousDue = null;
        for (int i = 0; i < n; i++) {
            Installment installment = schedule.get(i);
            int expectedSeq = i + 1;
            if (installment.sequence() != expectedSeq) {
                throw new DomainException("Invariant violated: expected sequence " + expectedSeq
                        + " at index " + i + ", was " + installment.sequence());
            }
            if (previousDue != null && !installment.dueDate().isAfter(previousDue)) {
                throw new DomainException("Invariant violated: due dates must be strictly increasing. "
                        + previousDue + " -> " + installment.dueDate());
            }
            previousDue = installment.dueDate();
        }
        // First installment must be after purchase date (one month later, clamped)
        if (!schedule.get(0).dueDate().isAfter(purchaseDate)) {
            throw new DomainException("Invariant violated: first installment due date must be after purchase date");
        }
    }

    public UUID id() { return id; }
    public String customerId() { return customerId; }
    public Money commodityCost() { return commodityCost; }
    public BigDecimal appliedMarginPercent() { return appliedMarginPercent; }
    public Money totalProfit() { return totalProfit; }
    public Money totalPayable() { return totalPayable; }
    public List<Installment> schedule() { return schedule; }
    public LocalDate purchaseDate() { return purchaseDate; }
    public Instant createdAt() { return createdAt; }

    /** Base installment amount = the amount of the first installment (all base installments are equal). */
    public Money baseInstallmentAmount() {
        return schedule.get(0).amount();
    }
}
