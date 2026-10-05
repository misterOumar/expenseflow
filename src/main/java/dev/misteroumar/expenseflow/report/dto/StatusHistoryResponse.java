package dev.misteroumar.expenseflow.report.dto;

import dev.misteroumar.expenseflow.report.ReportStatus;

import java.time.Instant;

public record StatusHistoryResponse(
        ReportStatus fromStatus,
        ReportStatus toStatus,
        String actorName,
        Instant at,
        String comment
) {
}