package dev.misteroumar.expenseflow.report.dto;

import dev.misteroumar.expenseflow.report.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LineResponse(
        Long id,
        LocalDate spentOn,
        ExpenseCategory category,
        String description,
        BigDecimal amountHt,
        BigDecimal vatAmount,
        BigDecimal amountTtc,
        Long receiptId
) {
}