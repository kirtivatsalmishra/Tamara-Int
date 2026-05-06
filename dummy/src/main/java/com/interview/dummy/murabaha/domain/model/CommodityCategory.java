package com.interview.dummy.murabaha.domain.model;

import com.interview.dummy.murabaha.api.error.DomainException;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalised commodity category code. Trimmed and lower-cased so that
 * {@code "Gold"}, {@code " gold "}, and {@code "gold"} all compare equal.
 */
public record CommodityCategory(String code) {

    public CommodityCategory {
        Objects.requireNonNull(code, "code");
        code = code.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new DomainException("CommodityCategory code must not be blank");
        }
    }

    public static CommodityCategory of(String raw) {
        return new CommodityCategory(raw);
    }
}
