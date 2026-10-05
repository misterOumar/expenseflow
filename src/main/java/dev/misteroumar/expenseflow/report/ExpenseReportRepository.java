package dev.misteroumar.expenseflow.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseReportRepository extends JpaRepository<ExpenseReport, Long> {

    List<ExpenseReport> findByOwnerIdOrderByPeriodYearDescPeriodMonthDesc(Long ownerId);

    boolean existsByOwnerIdAndPeriodMonthAndPeriodYear(Long ownerId, int periodMonth, int periodYear);
}