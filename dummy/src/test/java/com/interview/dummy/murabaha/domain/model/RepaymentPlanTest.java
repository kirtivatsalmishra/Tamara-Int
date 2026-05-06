package com.interview.dummy.murabaha.domain.model;

import com.interview.dummy.murabaha.api.error.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepaymentPlanTest {

    private static final Currency SAR = Currency.getInstance("SAR");
    private static final BigDecimal FLOOR = new BigDecimal("5.00");
    private static final LocalDate PURCHASE = LocalDate.parse("2026-05-06");

    @Test
    void validPlanConstructs() {
        RepaymentPlan plan = validPlan();
        assertThat(plan.id()).isNotNull();
        assertThat(plan.schedule()).hasSize(3);
        assertThat(plan.baseInstallmentAmount().amount()).isEqualByComparingTo(new BigDecimal("36.66"));
    }

    @Test
    void invariant1_commodityCostMustBePositive() {
        // Money.zero is not positive; pass a non-zero schedule so that the cost check is the failure point
        Money zeroCost = Money.zero(SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        List<Installment> nonZero = List.of(
                new Installment(1, PURCHASE.plusMonths(1), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(2, PURCHASE.plusMonths(2), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(3, PURCHASE.plusMonths(3), Money.of(new BigDecimal("36.68"), SAR))
        );
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", zeroCost, new BigDecimal("10.00"),
                Money.zero(SAR), payable,
                nonZero, PURCHASE, Instant.parse("2026-05-06T08:00:00Z"), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("commodityCost");
    }

    @Test
    void invariant2_installmentsBelowMinRejected() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        List<Installment> single = List.of(
                new Installment(1, PURCHASE.plusMonths(1), payable));
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("10.00"),
                profit, payable, single, PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("[2, 12]");
    }

    @Test
    void invariant2_installmentsAboveMaxRejected() {
        // 13 installments
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        List<Installment> many = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            many.add(new Installment(i, PURCHASE.plusMonths(i), Money.of(new BigDecimal("8.46"), SAR)));
        }
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("10.00"),
                profit, payable, many, PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("[2, 12]");
    }

    @Test
    void invariant3_marginBelowFloorRejected() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("4.00"), SAR);
        Money payable = Money.of(new BigDecimal("104.00"), SAR);
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("4.00"),
                profit, payable, schedule3(payable), PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("appliedMarginPercent");
    }

    @Test
    void invariant3_marginAbove100Rejected() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("100.00"), SAR);
        Money payable = Money.of(new BigDecimal("200.00"), SAR);
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("101.00"),
                profit, payable, schedule3(payable), PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("appliedMarginPercent");
    }

    @Test
    void invariant4_profitArithmeticChecked() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money wrongProfit = Money.of(new BigDecimal("99.00"), SAR);
        Money payable = Money.of(new BigDecimal("199.00"), SAR);
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("10.00"),
                wrongProfit, payable, schedule3(payable), PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("totalProfit");
    }

    @Test
    void invariant5_payableArithmeticChecked() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money wrongPayable = Money.of(new BigDecimal("999.00"), SAR);
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("10.00"),
                profit, wrongPayable, schedule3(wrongPayable), PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("totalPayable");
    }

    @Test
    void invariant6_sumOfInstallmentsMustEqualPayable() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        // Each installment is 33.33 -> sum 99.99 != 110.00
        List<Installment> bad = List.of(
                new Installment(1, PURCHASE.plusMonths(1), Money.of(new BigDecimal("33.33"), SAR)),
                new Installment(2, PURCHASE.plusMonths(2), Money.of(new BigDecimal("33.33"), SAR)),
                new Installment(3, PURCHASE.plusMonths(3), Money.of(new BigDecimal("33.33"), SAR))
        );
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("10.00"),
                profit, payable, bad, PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("sum(installments)");
    }

    @Test
    void invariant8_nonContiguousSequenceRejected() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        List<Installment> bad = List.of(
                new Installment(1, PURCHASE.plusMonths(1), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(3, PURCHASE.plusMonths(2), Money.of(new BigDecimal("36.67"), SAR)),
                new Installment(4, PURCHASE.plusMonths(3), Money.of(new BigDecimal("36.67"), SAR))
        );
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("10.00"),
                profit, payable, bad, PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("sequence");
    }

    @Test
    void invariantFirstInstallmentMustBeAfterPurchase() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        List<Installment> sameDay = List.of(
                new Installment(1, PURCHASE, Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(2, PURCHASE.plusMonths(1), Money.of(new BigDecimal("36.67"), SAR)),
                new Installment(3, PURCHASE.plusMonths(2), Money.of(new BigDecimal("36.67"), SAR))
        );
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("10.00"),
                profit, payable, sameDay, PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("first installment");
    }

    @Test
    void currencyMismatchOnTotalProfitRejected() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profitInKwd = Money.of(new BigDecimal("10.000"), Currency.getInstance("KWD"));
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "C1", cost, new BigDecimal("10.00"),
                profitInKwd, payable, schedule3(payable), PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void blankCustomerIdRejected() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        assertThatThrownBy(() -> new RepaymentPlan(
                UUID.randomUUID(), "  ", cost, new BigDecimal("10.00"),
                profit, payable, schedule3(payable), PURCHASE, Instant.now(), FLOOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("customerId");
    }

    private RepaymentPlan validPlan() {
        // 100 cost, 0% margin would zero-profit; use 10% for clean math
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        // 110 / 3 = 36.66, 36.66, 36.68
        List<Installment> schedule = List.of(
                new Installment(1, PURCHASE.plusMonths(1), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(2, PURCHASE.plusMonths(2), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(3, PURCHASE.plusMonths(3), Money.of(new BigDecimal("36.68"), SAR))
        );
        return new RepaymentPlan(
                UUID.randomUUID(), "CUST-1", cost, new BigDecimal("10.00"),
                profit, payable, schedule, PURCHASE, Instant.parse("2026-05-06T08:00:00Z"), FLOOR);
    }

    private List<Installment> schedule3(Money payable) {
        // Helper that splits a payable into 3 installments only for arithmetic invariant tests
        // where the schedule itself isn't the focus. Will not satisfy invariant 6 unless
        // payable matches its parts.
        BigDecimal amt = payable.amount().divide(new BigDecimal("3"), 2, java.math.RoundingMode.FLOOR);
        BigDecimal last = payable.amount().subtract(amt.multiply(new BigDecimal("2")));
        return List.of(
                new Installment(1, PURCHASE.plusMonths(1), Money.of(amt, SAR)),
                new Installment(2, PURCHASE.plusMonths(2), Money.of(amt, SAR)),
                new Installment(3, PURCHASE.plusMonths(3), Money.of(last, SAR))
        );
    }
}
