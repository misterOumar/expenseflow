package dev.misteroumar.expenseflow.report;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseReportRepository extends JpaRepository<ExpenseReport, Long> {

    boolean existsByOwnerIdAndPeriodMonthAndPeriodYear(Long ownerId, int periodMonth, int periodYear);

    /**
     * Notes visibles par un utilisateur : les siennes, celles de son équipe, et les notes
     * approuvées ou payées s'il est comptable. Le propriétaire est chargé dans la même requête
     * pour éviter une requête par note (N+1).
     *
     * @param status filtre optionnel ; {@code null} pour tous les statuts
     */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select r from ExpenseReport r
            where (r.owner.id = :userId
                   or r.owner.manager.id = :userId
                   or (:accountant = true and r.status in (
                       dev.misteroumar.expenseflow.report.ReportStatus.APPROVED,
                       dev.misteroumar.expenseflow.report.ReportStatus.PAID)))
              and (:status is null or r.status = :status)
            order by r.periodYear desc, r.periodMonth desc, r.id desc
            """)
    List<ExpenseReport> findVisible(@Param("userId") Long userId,
                                    @Param("accountant") boolean accountant,
                                    @Param("status") ReportStatus status);

    @Query("select l.report from ExpenseLine l where l.receipt.id = :receiptId")
    Optional<ExpenseReport> findByReceiptId(@Param("receiptId") Long receiptId);
}
