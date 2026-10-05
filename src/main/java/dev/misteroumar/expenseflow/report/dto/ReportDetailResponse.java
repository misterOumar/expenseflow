package dev.misteroumar.expenseflow.report.dto;

import dev.misteroumar.expenseflow.report.ReportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReportDetailResponse(
        Long id,
        String title,
        int periodMonth,
        int periodYear,
        ReportStatus status,
        BigDecimal totalAmount,
        Long ownerId,
        String ownerName,
        Instant submittedAt,
        Instant decidedAt,
        String decidedByName,
        String rejectionReason,
        List<LineResponse> lines
) {
}