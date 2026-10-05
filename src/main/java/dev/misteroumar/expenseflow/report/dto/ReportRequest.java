package dev.misteroumar.expenseflow.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotBlank @Size(max = 255) String title,
        @Min(1) @Max(12) int periodMonth,
        @Min(2000) @Max(2100) int periodYear
) {
}