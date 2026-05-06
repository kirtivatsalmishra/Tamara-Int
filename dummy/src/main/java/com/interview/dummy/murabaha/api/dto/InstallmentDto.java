package com.interview.dummy.murabaha.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Wire representation of a single installment row. */
public record InstallmentDto(int sequence, LocalDate dueDate, BigDecimal amount) { }
