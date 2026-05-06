package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.api.dto.CreatePlanRequest;
import com.interview.dummy.murabaha.api.error.IdempotencyKeyConflictException;
import com.interview.dummy.murabaha.api.error.PlanNotFoundException;
import com.interview.dummy.murabaha.api.error.UnknownCurrencyException;
import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import com.interview.dummy.murabaha.domain.policy.CommodityMarginPolicy;
import com.interview.dummy.murabaha.domain.policy.PromoCodePolicy;
import com.interview.dummy.murabaha.infrastructure.config.MurabahaProperties;
import com.interview.dummy.murabaha.infrastructure.money.CurrencyScaleProvider;
import com.interview.dummy.murabaha.infrastructure.money.MoneyAllocator;
import com.interview.dummy.murabaha.infrastructure.time.SaudiCalendar;
import com.interview.dummy.murabaha.policy.impl.GoldMarginPolicy;
import com.interview.dummy.murabaha.policy.impl.MetalsMarginPolicy;
import com.interview.dummy.murabaha.policy.impl.OilMarginPolicy;
import com.interview.dummy.murabaha.policy.impl.Save10PromoPolicy;
import com.interview.dummy.murabaha.policy.impl.Save20PromoPolicy;
import com.interview.dummy.murabaha.policy.impl.WheatMarginPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepaymentPlanServiceTest {

    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-05-06T08:00:00Z"), ZoneId.of("Asia/Riyadh"));

    private RepaymentPlanService service;
    private InMemoryPlanStore store;

    @BeforeEach
    void setUp() {
        MurabahaProperties properties = new MurabahaProperties();
        List<CommodityMarginPolicy> commodities = List.of(
                new GoldMarginPolicy(), new OilMarginPolicy(),
                new MetalsMarginPolicy(), new WheatMarginPolicy());
        List<PromoCodePolicy> promos = List.of(new Save10PromoPolicy(), new Save20PromoPolicy());
        MarginResolver resolver = new MarginResolver(commodities, promos, properties);
        SaudiCalendar calendar = new SaudiCalendar(FIXED);
        InstallmentScheduler scheduler = new InstallmentScheduler(
                calendar, new MoneyAllocator(new CurrencyScaleProvider()));
        ContractSummaryWriter writer = new EnglishContractSummaryWriter();
        store = new InMemoryPlanStore();
        service = new RepaymentPlanService(resolver, scheduler, writer, store, calendar, FIXED, properties);
    }

    @Test
    void createsPlanForGoldWithSave10WorkedExample() {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST-7421", new BigDecimal("5000.00"), "gold", 6, "SAVE10",
                LocalDate.parse("2026-05-06"), null);

        RepaymentPlanService.PlanWithSummary result = service.create(req, null);
        RepaymentPlan plan = result.plan();

        assertThat(plan.appliedMarginPercent()).isEqualByComparingTo(new BigDecimal("7.20"));
        assertThat(plan.totalProfit().amount()).isEqualByComparingTo(new BigDecimal("360.00"));
        assertThat(plan.totalPayable().amount()).isEqualByComparingTo(new BigDecimal("5360.00"));
        assertThat(plan.baseInstallmentAmount().amount()).isEqualByComparingTo(new BigDecimal("893.33"));
        assertThat(plan.schedule()).hasSize(6);
        assertThat(plan.schedule().get(5).amount().amount()).isEqualByComparingTo(new BigDecimal("893.35"));
        assertThat(plan.schedule().get(0).dueDate()).isEqualTo(LocalDate.parse("2026-06-06"));
        assertThat(plan.schedule().get(5).dueDate()).isEqualTo(LocalDate.parse("2026-11-06"));
        assertThat(result.contractSummary()).contains("SAR 5000.00").contains("SAR 5360.00");
    }

    @Test
    void usesTodayWhenPurchaseDateOmitted() {
        CreatePlanRequest req = new CreatePlanRequest(
                "C", new BigDecimal("100.00"), "metals", 3, null, null, null);
        RepaymentPlan plan = service.create(req, null).plan();
        assertThat(plan.purchaseDate()).isEqualTo(LocalDate.parse("2026-05-06"));
    }

    @Test
    void unknownCategoryFallsBackToDefault12Percent() {
        CreatePlanRequest req = new CreatePlanRequest(
                "C", new BigDecimal("1000.00"), "electronics", 2, null,
                LocalDate.parse("2026-05-06"), null);
        RepaymentPlan plan = service.create(req, null).plan();
        assertThat(plan.appliedMarginPercent()).isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void rejectsUnknownCurrency() {
        CreatePlanRequest req = new CreatePlanRequest(
                "C", new BigDecimal("100.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), "XYZ");
        assertThatThrownBy(() -> service.create(req, null))
                .isInstanceOf(UnknownCurrencyException.class);
    }

    @Test
    void usesRequestedCurrencyOverDefault() {
        CreatePlanRequest req = new CreatePlanRequest(
                "C", new BigDecimal("100.000"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), "KWD");
        RepaymentPlan plan = service.create(req, null).plan();
        assertThat(plan.commodityCost().currency().getCurrencyCode()).isEqualTo("KWD");
        assertThat(plan.commodityCost().amount().scale()).isEqualTo(3);
    }

    @Test
    void idempotencyReplayReturnsSamePlan() {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("1000.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        RepaymentPlan first = service.create(req, "key-1").plan();
        RepaymentPlan second = service.create(req, "key-1").plan();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void idempotencyConflictWhenBodyDiffers() {
        CreatePlanRequest req1 = new CreatePlanRequest(
                "CUST", new BigDecimal("1000.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        CreatePlanRequest req2 = new CreatePlanRequest(
                "CUST", new BigDecimal("2000.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        service.create(req1, "key-A");
        assertThatThrownBy(() -> service.create(req2, "key-A"))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void blankIdempotencyKeyDoesNotEnableReplay() {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("1000.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        RepaymentPlan p1 = service.create(req, "  ").plan();
        RepaymentPlan p2 = service.create(req, "  ").plan();
        assertThat(p2.id()).isNotEqualTo(p1.id());
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void getReturnsPlanById() {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("1000.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        RepaymentPlan saved = service.create(req, null).plan();
        RepaymentPlan loaded = service.get(saved.id()).plan();
        assertThat(loaded.id()).isEqualTo(saved.id());
    }

    @Test
    void getOnMissingThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(PlanNotFoundException.class);
    }

    /** Trivial in-memory store used to keep service tests JPA-free. */
    static class InMemoryPlanStore implements PlanStore {

        private final Map<UUID, RepaymentPlan> byId = new HashMap<>();
        private final Map<String, StoredPlan> byKey = new HashMap<>();

        @Override
        public RepaymentPlan save(RepaymentPlan plan, PlanRequestFingerprint fingerprint, String idempotencyKey) {
            byId.put(plan.id(), plan);
            if (idempotencyKey != null) {
                byKey.put(idempotencyKey, new StoredPlan(plan, fingerprint));
            }
            return plan;
        }

        @Override public Optional<RepaymentPlan> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }

        @Override public Optional<StoredPlan> findByIdempotencyKey(String key) {
            return Optional.ofNullable(byKey.get(key));
        }

        int size() { return byId.size(); }
    }
}
