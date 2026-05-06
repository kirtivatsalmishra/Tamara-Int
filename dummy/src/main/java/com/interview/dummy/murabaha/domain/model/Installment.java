package com.interview.dummy.murabaha.domain.model;

import com.interview.dummy.murabaha.api.error.DomainException;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One scheduled installment. Sequence is 1-based per the spec; due date is the
 * calendar day in the business zone (no time-of-day, no zone offset).
 */
public record Installment(int sequence, LocalDate dueDate, Money amount) {

    public Installment {
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(amount, "amount");
        if (sequence < 1) {
            throw new DomainException("Installment sequence must be >= 1, was " + sequence);
        }
        if (!amount.isPositive()) {
            throw new DomainException("Installment amount must be positive, was " + amount.amount());
        }
    }
}
