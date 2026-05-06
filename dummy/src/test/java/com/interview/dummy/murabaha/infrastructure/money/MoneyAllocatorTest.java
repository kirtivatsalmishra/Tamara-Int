package com.interview.dummy.murabaha.infrastructure.money;

import com.interview.dummy.murabaha.api.error.DomainException;
import com.interview.dummy.murabaha.domain.model.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyAllocatorTest {

    private static final Currency SAR = Currency.getInstance("SAR");
    private static final Currency KWD = Currency.getInstance("KWD");
    private static final Currency JPY = Currency.getInstance("JPY");

    private final MoneyAllocator allocator = new MoneyAllocator(new CurrencyScaleProvider());

    static Stream<Arguments> sarCases() {
        // §7.3 worked examples — must match verbatim.
        return Stream.of(
                Arguments.of("100.00", 3, List.of("33.33", "33.33", "33.34")),
                Arguments.of("100.00", 6, List.of("16.66", "16.66", "16.66", "16.66", "16.66", "16.70")),
                Arguments.of("99.99",  4, List.of("24.99", "24.99", "24.99", "25.02")),
                Arguments.of("1000.01", 12,
                        List.of("83.33", "83.33", "83.33", "83.33", "83.33", "83.33",
                                "83.33", "83.33", "83.33", "83.33", "83.33", "83.38"))
        );
    }

    @ParameterizedTest
    @MethodSource("sarCases")
    void splitsSarPerSpec(String total, int n, List<String> expected) {
        List<Money> result = allocator.split(Money.of(new BigDecimal(total), SAR), n);
        List<String> actual = result.stream().map(m -> m.amount().toPlainString()).toList();
        assertThat(actual).isEqualTo(expected);
        BigDecimal sum = result.stream().map(Money::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal(total));
    }

    @Test
    void splitsKwdAtScale3() {
        // 100.000 / 3 -> floor at scale 3 = 33.333; last absorbs: 100.000 - 33.333*2 = 33.334
        List<Money> result = allocator.split(Money.of(new BigDecimal("100.000"), KWD), 3);
        List<String> actual = result.stream().map(m -> m.amount().toPlainString()).toList();
        assertThat(actual).containsExactly("33.333", "33.333", "33.334");
        BigDecimal sum = result.stream().map(Money::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100.000"));
        // Every Money carries scale 3
        result.forEach(m -> assertThat(m.amount().scale()).isEqualTo(3));
    }

    @Test
    void splitsJpyAtScale0() {
        // 1000 / 3 floor = 333; last = 1000 - 666 = 334
        List<Money> result = allocator.split(Money.of(new BigDecimal("1000"), JPY), 3);
        List<String> actual = result.stream().map(m -> m.amount().toPlainString()).toList();
        assertThat(actual).containsExactly("333", "333", "334");
        result.forEach(m -> assertThat(m.amount().scale()).isEqualTo(0));
    }

    @Test
    void splitOfOneIsTotalItself() {
        List<Money> result = allocator.split(Money.of(new BigDecimal("42.42"), SAR), 1);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo(new BigDecimal("42.42"));
    }

    @Test
    void rejectsZeroN() {
        assertThatThrownBy(() -> allocator.split(Money.of(new BigDecimal("100"), SAR), 0))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsNegativeN() {
        assertThatThrownBy(() -> allocator.split(Money.of(new BigDecimal("100"), SAR), -1))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void lastInstallmentIsAlwaysGreaterOrEqualToBase() {
        // Using FLOOR base + last-absorbs guarantees last >= base.
        List<Money> result = allocator.split(Money.of(new BigDecimal("100.00"), SAR), 7);
        BigDecimal base = result.get(0).amount();
        BigDecimal last = result.get(result.size() - 1).amount();
        assertThat(last.compareTo(base)).isGreaterThanOrEqualTo(0);
    }
}
