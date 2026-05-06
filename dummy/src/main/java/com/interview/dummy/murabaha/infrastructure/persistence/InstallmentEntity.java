package com.interview.dummy.murabaha.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** JPA entity for a single installment row. */
@Entity
@Table(name = "installment")
public class InstallmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private RepaymentPlanEntity plan;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    /** Always {@code SCHEDULED} in MVP; future-proofed for payment workflows. */
    @Column(nullable = false, length = 16)
    private String status = "SCHEDULED";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RepaymentPlanEntity getPlan() { return plan; }
    public void setPlan(RepaymentPlanEntity plan) { this.plan = plan; }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
