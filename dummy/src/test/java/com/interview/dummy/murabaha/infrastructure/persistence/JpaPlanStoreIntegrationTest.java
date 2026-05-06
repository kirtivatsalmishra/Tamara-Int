package com.interview.dummy.murabaha.infrastructure.persistence;

import com.interview.dummy.murabaha.application.PlanRequestFingerprint;
import com.interview.dummy.murabaha.application.PlanStore;
import com.interview.dummy.murabaha.domain.model.Installment;
import com.interview.dummy.murabaha.domain.model.Money;
import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import com.interview.dummy.murabaha.infrastructure.config.MurabahaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaPlanStoreIntegrationTest {

    private static final Currency SAR = Currency.getInstance("SAR");

    @Autowired private PlanStore planStore;
    @Autowired private RepaymentPlanRepository repository;
    @Autowired private MurabahaProperties properties;

    @Test
    void roundTripsPlanThroughDatabase() {
        RepaymentPlan plan = samplePlan();
        PlanRequestFingerprint fingerprint = PlanRequestFingerprint.from(
                "CUST", new BigDecimal("100.00"), "SAR", "gold", 3, "SAVE10", LocalDate.parse("2026-05-06"));

        planStore.save(plan, fingerprint, null);
        RepaymentPlan loaded = planStore.findById(plan.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(plan.id());
        assertThat(loaded.customerId()).isEqualTo("CUST");
        assertThat(loaded.commodityCost().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(loaded.totalPayable().amount()).isEqualByComparingTo(new BigDecimal("110.00"));
        assertThat(loaded.schedule()).hasSize(3);
        assertThat(loaded.schedule().get(0).dueDate()).isEqualTo(LocalDate.parse("2026-06-06"));
    }

    @Test
    void findsByIdempotencyKey() {
        RepaymentPlan plan = samplePlan();
        PlanRequestFingerprint fp = PlanRequestFingerprint.from(
                "CUST", new BigDecimal("100.00"), "SAR", "gold", 3, "SAVE10", LocalDate.parse("2026-05-06"));
        planStore.save(plan, fp, "key-XYZ");

        PlanStore.StoredPlan stored = planStore.findByIdempotencyKey("key-XYZ").orElseThrow();
        assertThat(stored.plan().id()).isEqualTo(plan.id());
        assertThat(stored.fingerprint().commodityCategory()).isEqualTo("gold");
        assertThat(stored.fingerprint().promoCode()).isEqualTo("SAVE10");
    }

    @Test
    void persistsCurrencyAndAllowsScale3Kwd() {
        Currency kwd = Currency.getInstance("KWD");
        Money cost = Money.of(new BigDecimal("100.000"), kwd);
        Money profit = Money.of(new BigDecimal("10.000"), kwd);
        Money payable = Money.of(new BigDecimal("110.000"), kwd);
        // 110.000 / 3 -> floor = 36.666; last = 36.668
        List<Installment> schedule = List.of(
                new Installment(1, LocalDate.parse("2026-06-06"), Money.of(new BigDecimal("36.666"), kwd)),
                new Installment(2, LocalDate.parse("2026-07-06"), Money.of(new BigDecimal("36.666"), kwd)),
                new Installment(3, LocalDate.parse("2026-08-06"), Money.of(new BigDecimal("36.668"), kwd))
        );
        RepaymentPlan plan = new RepaymentPlan(
                UUID.randomUUID(), "CUST-KWD", cost, new BigDecimal("10.00"),
                profit, payable, schedule, LocalDate.parse("2026-05-06"),
                Instant.parse("2026-05-06T08:00:00Z"), properties.getMinMarginPercent());

        PlanRequestFingerprint fp = PlanRequestFingerprint.from(
                "CUST-KWD", new BigDecimal("100.000"), "KWD", "gold", 3, null, LocalDate.parse("2026-05-06"));
        planStore.save(plan, fp, null);

        RepaymentPlan loaded = planStore.findById(plan.id()).orElseThrow();
        assertThat(loaded.commodityCost().currency().getCurrencyCode()).isEqualTo("KWD");
        assertThat(loaded.commodityCost().amount().scale()).isEqualTo(3);
        assertThat(loaded.schedule().get(2).amount().amount()).isEqualByComparingTo(new BigDecimal("36.668"));
    }

    private RepaymentPlan samplePlan() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        // 110.00 / 3 -> 36.66, 36.66, 36.68
        List<Installment> schedule = List.of(
                new Installment(1, LocalDate.parse("2026-06-06"), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(2, LocalDate.parse("2026-07-06"), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(3, LocalDate.parse("2026-08-06"), Money.of(new BigDecimal("36.68"), SAR))
        );
        return new RepaymentPlan(
                UUID.randomUUID(), "CUST", cost, new BigDecimal("10.00"),
                profit, payable, schedule, LocalDate.parse("2026-05-06"),
                Instant.parse("2026-05-06T08:00:00Z"), properties.getMinMarginPercent());
    }
}
