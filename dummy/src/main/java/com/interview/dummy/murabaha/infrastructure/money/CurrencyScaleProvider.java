package com.interview.dummy.murabaha.infrastructure.money;

import org.springframework.stereotype.Component;

import java.util.Currency;

/**
 * Single source of truth for the number of decimal places a currency uses.
 *
 * <p>Implemented as a thin wrapper over {@link Currency#getDefaultFractionDigits()}
 * so that adding a new currency requires zero code changes (SAR=2, KWD=3, JPY=0
 * are all handled by the JDK).
 */
@Component
public class CurrencyScaleProvider {

    /** Returns the currency's standard scale (e.g. 2 for SAR, 3 for KWD, 0 for JPY). */
    public int scaleOf(Currency currency) {
        int defaultDigits = currency.getDefaultFractionDigits();
        // Currency.getDefaultFractionDigits() returns -1 for "non-decimal" or "unknown" entries
        // (e.g. XXX). Treat those as 2 to keep arithmetic well-defined.
        return defaultDigits < 0 ? 2 : defaultDigits;
    }
}
