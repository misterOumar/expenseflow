package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.report.dto.LineResponse;
import dev.misteroumar.expenseflow.report.dto.ReportDetailResponse;
import dev.misteroumar.expenseflow.report.dto.ReportSummaryResponse;
import dev.misteroumar.expenseflow.report.dto.StatusHistoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface ReportMapper {

    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerName", source = "owner.fullName")
    ReportSummaryResponse toSummary(ExpenseReport report);

    List<ReportSummaryResponse> toSummaries(List<ExpenseReport> reports);

    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerName", source = "owner.fullName")
    @Mapping(target = "decidedByName", source = "decidedBy.fullName")
    ReportDetailResponse toDetail(ExpenseReport report);

    @Mapping(target = "receiptId", source = "receipt.id")
    LineResponse toLine(ExpenseLine line);

    @Mapping(target = "actorName", source = "actor.fullName")
    StatusHistoryResponse toHistory(StatusHistory history);

    List<StatusHistoryResponse> toHistories(List<StatusHistory> history);
}