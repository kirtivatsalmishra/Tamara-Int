package com.interview.dummy.murabaha.api.error;

/**
 * Thrown when a request supplies a currency code that {@link java.util.Currency}
 * does not recognise. Surfaced as a dedicated 400 problem detail so clients can
 * distinguish a malformed currency from a generic validation failure.
 */
public class UnknownCurrencyException extends RuntimeException {

    private final String currencyCode;

    public UnknownCurrencyException(String currencyCode) {
        super("Unknown ISO-4217 currency code: " + currencyCode);
        this.currencyCode = currencyCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
}
