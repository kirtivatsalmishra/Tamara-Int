package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.api.dto.CreatePlanRequest;
import com.interview.dummy.murabaha.api.error.IdempotencyKeyConflictException;
import com.interview.dummy.murabaha.api.error.PlanNotFoundException;
import com.interview.dummy.murabaha.api.error.UnknownCurrencyException;
import com.interview.dummy.murabaha.domain.model.CommodityCategory;
import com.interview.dummy.murabaha.domain.model.Installment;
import com.interview.dummy.murabaha.domain.model.Money;
import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import com.interview.dummy.murabaha.infrastructure.config.MurabahaProperties;
import com.interview.dummy.murabaha.infrastructure.time.SaudiCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates margin resolution, scheduling, aggregate construction, and
 * persistence into a single transaction. Idempotency replay/conflict logic
 * lives here so callers (controllers, batch jobs) don't have to reimplement it.
 */
@Service
public class RepaymentPlanService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final MarginResolver marginResolver;
    private final InstallmentScheduler scheduler;
    private final ContractSummaryWriter summaryWriter;
    private final PlanStore planStore;
    private final SaudiCalendar calendar;
    private final Clock clock;
    private final MurabahaProperties properties;

    public RepaymentPlanService(MarginResolver marginResolver,
                                InstallmentScheduler scheduler,
                                ContractSummaryWriter summaryWriter,
                                PlanStore planStore,
                                SaudiCalendar calendar,
                                Clock clock,
                                MurabahaProperties properties) {
        this.marginResolver = marginResolver;
        this.scheduler = scheduler;
        this.summaryWriter = summaryWriter;
        this.planStore = planStore;
        this.calendar = calendar;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Creates a plan from the request. If {@code idempotencyKey} is non-blank
     * and a plan already exists under that key, returns the original plan when
     * the fingerprint matches, or throws {@link IdempotencyKeyConflictException}
     * when the inputs differ.
     */
    @Transactional
    public PlanWithSummary create(CreatePlanRequest request, String idempotencyKey) {
        Currency currency = resolveCurrency(request.currencyCode());
        LocalDate purchaseDate = request.purchaseDate() != null ? request.purchaseDate() : calendar.today();
        PlanRequestFingerprint fingerprint = fingerprintOf(request, currency, purchaseDate);

        if (isUsableKey(idempotencyKey)) {
            Optional<PlanStore.StoredPlan> existing = planStore.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                PlanStore.StoredPlan stored = existing.get();
                if (!stored.fingerprint().matches(fingerprint)) {
                    throw new IdempotencyKeyConflictException(idempotencyKey);
                }
                return new PlanWithSummary(stored.plan(), summaryWriter.write(stored.plan()));
            }
        }

        CommodityCategory category = CommodityCategory.of(request.commodityCategory());
        BigDecimal appliedMarginPercent = marginResolver.resolve(category, request.promoCode());

        Money commodityCost = Money.of(request.commodityCost(), currency);
        Money totalProfit = computeProfit(commodityCost, appliedMarginPercent);
        Money totalPayable = commodityCost.add(totalProfit);
        List<Installment> schedule = scheduler.build(totalPayable, request.installments(), purchaseDate);

        RepaymentPlan plan = new RepaymentPlan(
                UUID.randomUUID(),
                request.customerId(),
                commodityCost,
                appliedMarginPercent,
                totalProfit,
                totalPayable,
                schedule,
                purchaseDate,
                Instant.now(clock),
                properties.getMinMarginPercent());

        RepaymentPlan saved = planStore.save(plan, fingerprint, isUsableKey(idempotencyKey) ? idempotencyKey : null);
        return new PlanWithSummary(saved, summaryWriter.write(saved));
    }

    @Transactional(readOnly = true)
    public PlanWithSummary get(UUID id) {
        RepaymentPlan plan = planStore.findById(id).orElseThrow(() -> new PlanNotFoundException(id));
        return new PlanWithSummary(plan, summaryWriter.write(plan));
    }

    private static boolean isUsableKey(String key) {
        return key != null && !key.isBlank();
    }

    private Money computeProfit(Money commodityCost, BigDecimal appliedMarginPercent) {
        int scale = commodityCost.amount().scale();
        BigDecimal rawProfit = commodityCost.amount()
                .multiply(appliedMarginPercent)
                .divide(HUNDRED, scale, RoundingMode.HALF_EVEN);
        return Money.of(rawProfit, commodityCost.currency());
    }

    private Currency resolveCurrency(String requestedCode) {
        String code = requestedCode == null || requestedCode.isBlank()
                ? properties.getCurrencyCode()
                : requestedCode.trim().toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException ex) {
            throw new UnknownCurrencyException(code);
        }
    }

    private PlanRequestFingerprint fingerprintOf(CreatePlanRequest request, Currency currency, LocalDate purchaseDate) {
        return PlanRequestFingerprint.from(
                request.customerId(),
                request.commodityCost(),
                currency.getCurrencyCode(),
                request.commodityCategory(),
                request.installments(),
                request.promoCode(),
                purchaseDate);
    }

    /** Tuple returned to the controller: domain plan + its rendered summary. */
    public record PlanWithSummary(RepaymentPlan plan, String contractSummary) { }
}
