package com.interview.dummy.murabaha.infrastructure.persistence;

import com.interview.dummy.murabaha.api.error.DomainException;
import com.interview.dummy.murabaha.application.PlanRequestFingerprint;
import com.interview.dummy.murabaha.domain.model.Installment;
import com.interview.dummy.murabaha.domain.model.Money;
import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import com.interview.dummy.murabaha.infrastructure.config.MurabahaProperties;
import com.interview.dummy.murabaha.infrastructure.money.CurrencyScaleProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Translates between the JPA entity and the domain aggregate. Asserts that the
 * persisted scale matches the currency scale on read; a mismatch indicates the
 * database has drifted and is treated as a programmer error.
 */
@Component
public class PlanMapper {

    private final MurabahaProperties properties;
    private final CurrencyScaleProvider scaleProvider;

    public PlanMapper(MurabahaProperties properties, CurrencyScaleProvider scaleProvider) {
        this.properties = properties;
        this.scaleProvider = scaleProvider;
    }

    public RepaymentPlanEntity toEntity(RepaymentPlan plan,
                                        PlanRequestFingerprint fingerprint,
                                        String idempotencyKey) {
        RepaymentPlanEntity entity = new RepaymentPlanEntity();
        entity.setId(plan.id());
        entity.setCustomerId(plan.customerId());
        entity.setCommodityCost(plan.commodityCost().amount());
        entity.setAppliedMarginPercent(plan.appliedMarginPercent());
        entity.setTotalProfit(plan.totalProfit().amount());
        entity.setTotalPayable(plan.totalPayable().amount());
        entity.setCurrencyCode(plan.commodityCost().currency().getCurrencyCode());
        entity.setPurchaseDate(plan.purchaseDate());
        entity.setCreatedAt(plan.createdAt());
        entity.setCommodityCategory(fingerprint.commodityCategory());
        entity.setPromoCode(fingerprint.promoCode());
        entity.setIdempotencyKey(idempotencyKey);

        List<InstallmentEntity> installmentEntities = new ArrayList<>(plan.schedule().size());
        for (Installment installment : plan.schedule()) {
            InstallmentEntity ie = new InstallmentEntity();
            ie.setPlan(entity);
            ie.setSequence(installment.sequence());
            ie.setDueDate(installment.dueDate());
            ie.setAmount(installment.amount().amount());
            installmentEntities.add(ie);
        }
        entity.setInstallments(installmentEntities);
        return entity;
    }

    public RepaymentPlan toDomain(RepaymentPlanEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrencyCode());
        int scale = scaleProvider.scaleOf(currency);

        Money commodityCost = Money.of(rescale(entity.getCommodityCost(), scale), currency);
        Money totalProfit = Money.of(rescale(entity.getTotalProfit(), scale), currency);
        Money totalPayable = Money.of(rescale(entity.getTotalPayable(), scale), currency);

        List<Installment> schedule = new ArrayList<>(entity.getInstallments().size());
        for (InstallmentEntity ie : entity.getInstallments()) {
            Money amt = Money.of(rescale(ie.getAmount(), scale), currency);
            schedule.add(new Installment(ie.getSequence(), ie.getDueDate(), amt));
        }

        return new RepaymentPlan(
                entity.getId(),
                entity.getCustomerId(),
                commodityCost,
                normaliseMarginPercent(entity.getAppliedMarginPercent()),
                totalProfit,
                totalPayable,
                schedule,
                entity.getPurchaseDate(),
                entity.getCreatedAt(),
                properties.getMinMarginPercent());
    }

    /**
     * The margin column is {@code precision=7, scale=4} so values come back at
     * scale 4 (e.g. {@code 12.0000}). The domain prefers scale 2 for display
     * stability ({@code 12.00}). Strip trailing zeros and clamp the scale to a
     * minimum of 2.
     */
    private BigDecimal normaliseMarginPercent(BigDecimal raw) {
        BigDecimal stripped = raw.stripTrailingZeros();
        if (stripped.scale() < 2) {
            return stripped.setScale(2, java.math.RoundingMode.UNNECESSARY);
        }
        return stripped;
    }

    public PlanRequestFingerprint toFingerprint(RepaymentPlanEntity entity) {
        return PlanRequestFingerprint.from(
                entity.getCustomerId(),
                entity.getCommodityCost(),
                entity.getCurrencyCode(),
                entity.getCommodityCategory(),
                entity.getInstallments().size(),
                entity.getPromoCode(),
                entity.getPurchaseDate());
    }

    private BigDecimal rescale(BigDecimal raw, int targetScale) {
        if (raw.scale() == targetScale) {
            return raw;
        }
        // The DB column is precision=19 scale=4; the domain expects currency scale (2 for SAR, 3 for KWD).
        // Trailing zeros at scale 4 are safe to drop down to currency scale; non-zero trailing digits
        // indicate corruption.
        BigDecimal stripped = raw.stripTrailingZeros();
        if (stripped.scale() > targetScale) {
            throw new DomainException("Persisted amount " + raw + " has more precision than currency scale " + targetScale);
        }
        return raw.setScale(targetScale, java.math.RoundingMode.UNNECESSARY);
    }
}
