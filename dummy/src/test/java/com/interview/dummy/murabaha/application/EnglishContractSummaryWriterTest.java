package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.domain.model.Installment;
import com.interview.dummy.murabaha.domain.model.Money;
import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EnglishContractSummaryWriterTest {

    private final EnglishContractSummaryWriter writer = new EnglishContractSummaryWriter();
    private static final BigDecimal FLOOR = new BigDecimal("5.00");

    @Test
    void summaryMatchesSpecTemplateForSar() {
        RepaymentPlan plan = sarPlan();
        String summary = writer.write(plan);
        assertThat(summary).isEqualTo(
                "This Murabaha contract confirms our purchase of the commodity on your behalf for SAR 5000.00. "
                        + "We are selling it to you at a total price of SAR 5360.00, "
                        + "which includes our profit of SAR 360.00. "
                        + "This amount is to be paid in 6 equal monthly installments of SAR 893.33."
        );
    }

    @Test
    void summaryUsesRequestCurrencyNotHardcodedSar() {
        Currency kwd = Currency.getInstance("KWD");
        Money cost = Money.of(new BigDecimal("100.000"), kwd);
        Money profit = Money.of(new BigDecimal("10.000"), kwd);
        Money payable = Money.of(new BigDecimal("110.000"), kwd);
        // 110.000 / 3 -> 36.666 / 36.666 / 36.668
        List<Installment> schedule = List.of(
                new Installment(1, LocalDate.parse("2026-06-06"), Money.of(new BigDecimal("36.666"), kwd)),
                new Installment(2, LocalDate.parse("2026-07-06"), Money.of(new BigDecimal("36.666"), kwd)),
                new Installment(3, LocalDate.parse("2026-08-06"), Money.of(new BigDecimal("36.668"), kwd))
        );
        RepaymentPlan plan = new RepaymentPlan(
                UUID.randomUUID(), "C", cost, new BigDecimal("10.00"),
                profit, payable, schedule, LocalDate.parse("2026-05-06"),
                Instant.parse("2026-05-06T08:00:00Z"), FLOOR);
        String summary = writer.write(plan);
        assertThat(summary).contains("KWD 100.000")
                .contains("KWD 110.000")
                .contains("KWD 10.000")
                .contains("3 equal monthly installments of KWD 36.666");
        assertThat(summary).doesNotContain("SAR");
    }

    private RepaymentPlan sarPlan() {
        Currency sar = Currency.getInstance("SAR");
        Money cost = Money.of(new BigDecimal("5000.00"), sar);
        Money profit = Money.of(new BigDecimal("360.00"), sar);
        Money payable = Money.of(new BigDecimal("5360.00"), sar);
        // 5360 / 6 floor at scale 2 = 893.33; last = 5360 - 893.33*5 = 5360 - 4466.65 = 893.35
        List<Installment> schedule = List.of(
                new Installment(1, LocalDate.parse("2026-06-06"), Money.of(new BigDecimal("893.33"), sar)),
                new Installment(2, LocalDate.parse("2026-07-06"), Money.of(new BigDecimal("893.33"), sar)),
                new Installment(3, LocalDate.parse("2026-08-06"), Money.of(new BigDecimal("893.33"), sar)),
                new Installment(4, LocalDate.parse("2026-09-06"), Money.of(new BigDecimal("893.33"), sar)),
                new Installment(5, LocalDate.parse("2026-10-06"), Money.of(new BigDecimal("893.33"), sar)),
                new Installment(6, LocalDate.parse("2026-11-06"), Money.of(new BigDecimal("893.35"), sar))
        );
        return new RepaymentPlan(
                UUID.randomUUID(), "CUST-7421", cost, new BigDecimal("7.20"),
                profit, payable, schedule, LocalDate.parse("2026-05-06"),
                Instant.parse("2026-05-06T08:00:00Z"), FLOOR);
    }
}
