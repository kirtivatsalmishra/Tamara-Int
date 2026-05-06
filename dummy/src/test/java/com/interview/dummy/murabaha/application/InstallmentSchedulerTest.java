package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.domain.model.Installment;
import com.interview.dummy.murabaha.domain.model.Money;
import com.interview.dummy.murabaha.infrastructure.money.CurrencyScaleProvider;
import com.interview.dummy.murabaha.infrastructure.money.MoneyAllocator;
import com.interview.dummy.murabaha.infrastructure.time.SaudiCalendar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentSchedulerTest {

    private static final Currency SAR = Currency.getInstance("SAR");

    private final InstallmentScheduler scheduler = new InstallmentScheduler(
            new SaudiCalendar(Clock.systemUTC()),
            new MoneyAllocator(new CurrencyScaleProvider()));

    @Test
    void buildsScheduleWithClampedDatesFromOriginalPurchase() {
        // Purchase Jan 31, N=3, total 100.00. Each due date computed from base + i months.
        Money total = Money.of(new BigDecimal("100.00"), SAR);
        LocalDate purchase = LocalDate.parse("2026-01-31");
        List<Installment> schedule = scheduler.build(total, 3, purchase);

        assertThat(schedule).hasSize(3);
        assertThat(schedule.get(0).dueDate()).isEqualTo(LocalDate.parse("2026-02-28"));
        assertThat(schedule.get(1).dueDate()).isEqualTo(LocalDate.parse("2026-03-31"));
        assertThat(schedule.get(2).dueDate()).isEqualTo(LocalDate.parse("2026-04-30"));
        assertThat(schedule.get(0).amount().amount()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(schedule.get(1).amount().amount()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(schedule.get(2).amount().amount()).isEqualByComparingTo(new BigDecimal("33.34"));
    }

    @Test
    void sequenceIsOneBased() {
        Money total = Money.of(new BigDecimal("60.00"), SAR);
        LocalDate purchase = LocalDate.parse("2026-05-06");
        List<Installment> schedule = scheduler.build(total, 6, purchase);
        assertThat(schedule).extracting(Installment::sequence).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(schedule.get(0).dueDate()).isEqualTo(LocalDate.parse("2026-06-06"));
        assertThat(schedule.get(5).dueDate()).isEqualTo(LocalDate.parse("2026-11-06"));
    }
}
