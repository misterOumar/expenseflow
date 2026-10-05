package dev.misteroumar.expenseflow.report.dto;

import dev.misteroumar.expenseflow.report.ReportStatus;

import java.math.BigDecimal;

public record ReportSummaryResponse(
        Long id,
        String title,
        int periodMonth,
        int periodYear,
        ReportStatus status,
        BigDecimal totalAmount,
        Long ownerId,
        String ownerName
) {
}