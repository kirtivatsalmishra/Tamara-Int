package com.interview.dummy.murabaha.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity backing {@code repayment_plan}. Kept in the infrastructure layer
 * so the domain model remains JPA-free.
 *
 * <p>Idempotency: {@code idempotency_key} is unique when present. The
 * {@code commodity_category} and {@code promo_code} columns persist the raw
 * inputs needed to reconstruct the request fingerprint for replay/conflict
 * checks.
 */
@Entity
@Table(
        name = "repayment_plan",
        uniqueConstraints = @UniqueConstraint(name = "uk_repayment_plan_idempotency_key", columnNames = "idempotency_key")
)
public class RepaymentPlanEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal commodityCost;

    @Column(precision = 7, scale = 4, nullable = false)
    private BigDecimal appliedMarginPercent;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal totalProfit;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal totalPayable;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String commodityCategory;

    private String promoCode;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sequence ASC")
    private List<InstallmentEntity> installments = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public BigDecimal getCommodityCost() { return commodityCost; }
    public void setCommodityCost(BigDecimal commodityCost) { this.commodityCost = commodityCost; }

    public BigDecimal getAppliedMarginPercent() { return appliedMarginPercent; }
    public void setAppliedMarginPercent(BigDecimal appliedMarginPercent) { this.appliedMarginPercent = appliedMarginPercent; }

    public BigDecimal getTotalProfit() { return totalProfit; }
    public void setTotalProfit(BigDecimal totalProfit) { this.totalProfit = totalProfit; }

    public BigDecimal getTotalPayable() { return totalPayable; }
    public void setTotalPayable(BigDecimal totalPayable) { this.totalPayable = totalPayable; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCommodityCategory() { return commodityCategory; }
    public void setCommodityCategory(String commodityCategory) { this.commodityCategory = commodityCategory; }

    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public List<InstallmentEntity> getInstallments() { return installments; }
    public void setInstallments(List<InstallmentEntity> installments) { this.installments = installments; }
}
