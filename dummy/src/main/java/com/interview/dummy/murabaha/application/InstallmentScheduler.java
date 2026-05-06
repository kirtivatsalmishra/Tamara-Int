package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.domain.model.Installment;
import com.interview.dummy.murabaha.domain.model.Money;
import com.interview.dummy.murabaha.infrastructure.money.MoneyAllocator;
import com.interview.dummy.murabaha.infrastructure.time.SaudiCalendar;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Composes {@link MoneyAllocator} (split amounts) and {@link SaudiCalendar}
 * (clamped monthly dates) into a deterministic installment schedule.
 *
 * <p>Per §6.4 each due date is computed from {@code purchaseDate + i months},
 * never from the previous installment.
 */
@Component
public class InstallmentScheduler {

    private final SaudiCalendar calendar;
    private final MoneyAllocator allocator;

    public InstallmentScheduler(SaudiCalendar calendar, MoneyAllocator allocator) {
        this.calendar = calendar;
        this.allocator = allocator;
    }

    public List<Installment> build(Money totalPayable, int installments, LocalDate purchaseDate) {
        List<Money> amounts = allocator.split(totalPayable, installments);
        List<Installment> schedule = new ArrayList<>(installments);
        for (int i = 0; i < installments; i++) {
            int sequence = i + 1;
            LocalDate dueDate = calendar.addMonthsClamped(purchaseDate, sequence);
            schedule.add(new Installment(sequence, dueDate, amounts.get(i)));
        }
        return schedule;
    }
}
