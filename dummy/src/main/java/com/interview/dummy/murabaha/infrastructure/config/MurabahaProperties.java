package com.interview.dummy.murabaha.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Externalised policy parameters for the Murabaha module.
 *
 * <p>Defaults are in {@code application.yml} under the {@code murabaha} prefix.
 * The values here are policy, not code: changing the floor in regulation should
 * be a YAML edit, not a redeploy of a constant.
 */
@Validated
@ConfigurationProperties(prefix = "murabaha")
public class MurabahaProperties {

    /** Margin applied when no commodity-specific policy claims the category. */
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal defaultMarginPercent = new BigDecimal("12.00");

    /** Lower bound for the applied margin after promo adjustment. */
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal minMarginPercent = new BigDecimal("5.00");

    /** Default ISO-4217 currency code when the request omits one. */
    @NotBlank
    private String currencyCode = "SAR";

    /** Business time-zone used by {@code SaudiCalendar} and the {@code Clock} bean. */
    @NotBlank
    private String businessZone = "Asia/Riyadh";

    public BigDecimal getDefaultMarginPercent() {
        return defaultMarginPercent;
    }

    public void setDefaultMarginPercent(BigDecimal defaultMarginPercent) {
        this.defaultMarginPercent = defaultMarginPercent;
    }

    public BigDecimal getMinMarginPercent() {
        return minMarginPercent;
    }

    public void setMinMarginPercent(BigDecimal minMarginPercent) {
        this.minMarginPercent = minMarginPercent;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getBusinessZone() {
        return businessZone;
    }

    public void setBusinessZone(String businessZone) {
        this.businessZone = businessZone;
    }
}