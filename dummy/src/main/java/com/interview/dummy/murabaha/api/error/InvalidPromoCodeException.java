package com.interview.dummy.murabaha.api.error;

/** Thrown when a request supplies a promo code no policy claims. */
public class InvalidPromoCodeException extends RuntimeException {

    private final String promoCode;

    public InvalidPromoCodeException(String promoCode) {
        super("Unknown promo code: " + promoCode);
        this.promoCode = promoCode;
    }

    public String getPromoCode() {
        return promoCode;
    }
}
