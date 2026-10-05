package dev.misteroumar.expenseflow.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByReportIdOrderByAtAsc(Long reportId);
}