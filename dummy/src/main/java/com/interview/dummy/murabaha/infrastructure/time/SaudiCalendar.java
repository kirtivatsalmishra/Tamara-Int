package com.interview.dummy.murabaha.infrastructure.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * The single sanctioned source for "today" and for monthly date arithmetic in
 * the Murabaha module. No code outside this class should call
 * {@link LocalDate#now()} — doing so makes scheduling non-deterministic and
 * untestable.
 *
 * <p>{@link #addMonthsClamped(LocalDate, int)} always computes the offset from
 * the supplied {@code base}, never iteratively from a previous installment.
 * This is what avoids the well-known Jan 31 → Feb 28 → Mar 28 drift bug.
 */
@Component
public class SaudiCalendar {

    /** Default business zone; the active value is whatever the {@link Clock} bean is configured with. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Riyadh");

    private final Clock clock;

    public SaudiCalendar(Clock clock) {
        this.clock = clock;
    }

    /** Current date in the business zone. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Returns {@code base + months}, clamping the day-of-month to the target
     * month's length when the original day overflows.
     *
     * <p>Example: {@code addMonthsClamped(2026-01-31, 1) == 2026-02-28}.
     * Critically, computing the next month again from the original base date
     * yields {@code 2026-03-31}, not {@code 2026-03-28}.
     */
    public LocalDate addMonthsClamped(LocalDate base, int months) {
        YearMonth target = YearMonth.from(base).plusMonths(months);
        int day = Math.min(base.getDayOfMonth(), target.lengthOfMonth());
        return target.atDay(day);
    }
}
