package dev.misteroumar.expenseflow.report.dto;

import dev.misteroumar.expenseflow.report.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LineRequest(
        @NotNull @PastOrPresent LocalDate spentOn,
        @NotNull ExpenseCategory category,
        @NotBlank @Size(max = 500) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amountHt,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal vatAmount,
        Long receiptId
) {
}