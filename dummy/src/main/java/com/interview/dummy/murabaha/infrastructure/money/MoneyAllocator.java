package com.interview.dummy.murabaha.infrastructure.money;

import com.interview.dummy.murabaha.api.error.DomainException;
import com.interview.dummy.murabaha.domain.model.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Splits a total {@link Money} into {@code n} parts so that the sum reconciles
 * exactly. The algorithm: floor of {@code total / n} at currency scale for the
 * first {@code n - 1} installments, with the last installment receiving
 * {@code total - sum(previous)} — an exact subtraction with no rounding.
 *
 * <p>Per §7.3 of the TDD this guarantees {@code last >= base}. {@code FLOOR} is
 * deliberate: rounding the base up would produce a smaller last installment,
 * which contradicts "the last installment absorbs the rounding".
 */
@Component
public class MoneyAllocator {

    private final CurrencyScaleProvider scaleProvider;

    public MoneyAllocator(CurrencyScaleProvider scaleProvider) {
        this.scaleProvider = scaleProvider;
    }

    public List<Money> split(Money total, int n) {
        if (n < 1) {
            throw new DomainException("Cannot split into fewer than 1 part: n=" + n);
        }
        Currency currency = total.currency();
        int scale = scaleProvider.scaleOf(currency);

        BigDecimal totalAmount = total.amount();
        BigDecimal divisor = BigDecimal.valueOf(n);
        BigDecimal base = totalAmount.divide(divisor, scale, RoundingMode.FLOOR);

        List<Money> out = new ArrayList<>(n);
        for (int i = 0; i < n - 1; i++) {
            out.add(new Money(base, currency));
        }
        BigDecimal sumOfFirstNMinusOne = base.multiply(BigDecimal.valueOf(n - 1L));
        BigDecimal lastAmount = totalAmount.subtract(sumOfFirstNMinusOne);
        out.add(new Money(lastAmount, currency));

        // Defence-in-depth: prove the reconciliation. A failure here is a bug, not user input.
        BigDecimal sum = out.stream().map(Money::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalAmount) != 0) {
            throw new DomainException("MoneyAllocator failed to reconcile: sum=" + sum + " total=" + totalAmount);
        }
        return out;
    }
}
