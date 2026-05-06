package com.interview.dummy.murabaha.infrastructure.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SaudiCalendarTest {

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    @Test
    void todayUsesInjectedClock() {
        Clock fixed = Clock.fixed(Instant.parse("2026-05-06T08:00:00Z"), RIYADH);
        SaudiCalendar calendar = new SaudiCalendar(fixed);
        assertThat(calendar.today()).isEqualTo(LocalDate.parse("2026-05-06"));
    }

    static Stream<Arguments> clampedCases() {
        // Worked examples from §6.4. Every row computes from the ORIGINAL base, not from
        // the previous installment — that's the whole point of the design.
        return Stream.of(
                // Jan 31 -> Feb 28 -> Mar 31 -> Apr 30 (non-leap)
                Arguments.of("2026-01-31", 1, "2026-02-28"),
                Arguments.of("2026-01-31", 2, "2026-03-31"),
                Arguments.of("2026-01-31", 3, "2026-04-30"),
                // Leap year: Jan 31, 2024 -> Feb 29
                Arguments.of("2024-01-31", 1, "2024-02-29"),
                Arguments.of("2024-01-31", 2, "2024-03-31"),
                Arguments.of("2024-01-31", 3, "2024-04-30"),
                // Mid-month, no clamping required
                Arguments.of("2026-03-15", 1, "2026-04-15"),
                Arguments.of("2026-03-15", 2, "2026-05-15"),
                Arguments.of("2026-03-15", 3, "2026-06-15"),
                // 12-month rollover
                Arguments.of("2026-05-06", 12, "2027-05-06"),
                // Day 30 -> Feb (clamped)
                Arguments.of("2026-01-30", 1, "2026-02-28")
        );
    }

    @ParameterizedTest
    @MethodSource("clampedCases")
    void addMonthsClampedMatchesTddTable(String base, int months, String expected) {
        SaudiCalendar calendar = new SaudiCalendar(Clock.systemUTC());
        LocalDate result = calendar.addMonthsClamped(LocalDate.parse(base), months);
        assertThat(result).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void addMonthsClampedDoesNotDriftWhenCalledIteratively() {
        // The trap: if you compute from the previous installment, Jan 31 -> Feb 28 -> Mar 28.
        // We compute from the ORIGINAL base, so installment 2 is Mar 31.
        SaudiCalendar calendar = new SaudiCalendar(Clock.systemUTC());
        LocalDate purchase = LocalDate.parse("2026-01-31");
        LocalDate i1 = calendar.addMonthsClamped(purchase, 1);
        LocalDate i2 = calendar.addMonthsClamped(purchase, 2);
        assertThat(i1).isEqualTo(LocalDate.parse("2026-02-28"));
        assertThat(i2).isEqualTo(LocalDate.parse("2026-03-31"));
    }
}
