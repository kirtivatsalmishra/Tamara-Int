package com.interview.dummy.murabaha.domain.model;

import com.interview.dummy.murabaha.api.error.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable currency-aware monetary value. Constructing a {@code Money} always
 * normalises the amount to the currency's natural scale using
 * {@link RoundingMode#HALF_EVEN} so that downstream code can never observe an
 * unscaled amount.
 *
 * <p>The scale chosen here is the JDK-default for the currency
 * ({@code Currency.getDefaultFractionDigits()}): SAR=2, KWD=3, JPY=0.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int scale = currency.getDefaultFractionDigits();
        if (scale < 0) {
            scale = 2;
        }
        amount = amount.setScale(scale, RoundingMode.HALF_EVEN);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /** Returns true if {@code amount > 0}. */
    public boolean isPositive() {
        return amount.signum() > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new DomainException("Currency mismatch: " + currency.getCurrencyCode()
                    + " vs " + other.currency.getCurrencyCode());
        }
    }
}
