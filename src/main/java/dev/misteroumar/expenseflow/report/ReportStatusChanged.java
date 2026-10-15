package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.user.User;

import java.math.BigDecimal;

/**
 * Événement publié à chaque transition de statut.
 * <p>
 * Il ne contient que des valeurs copiées au moment de la publication, aucune entité JPA :
 * ses écouteurs s'exécutent après le commit, quand la session Hibernate est fermée.
 *
 * @param managerEmail {@code null} si le propriétaire n'a pas de manager
 * @param comment      motif de rejet, ou {@code null}
 */
public record ReportStatusChanged(
        Long reportId,
        String title,
        int periodMonth,
        int periodYear,
        BigDecimal totalAmount,
        ReportStatus from,
        ReportStatus to,
        String ownerName,
        String ownerEmail,
        String managerName,
        String managerEmail,
        String actorName,
        String comment
) {

    static ReportStatusChanged of(ExpenseReport report, ReportStatus from, User actor, String comment) {
        User owner = report.getOwner();
        User manager = owner.getManager();
        return new ReportStatusChanged(report.getId(), report.getTitle(), report.getPeriodMonth(),
                report.getPeriodYear(), report.getTotalAmount(), from, report.getStatus(),
                owner.getFullName(), owner.getEmail(),
                manager == null ? null : manager.getFullName(),
                manager == null ? null : manager.getEmail(),
                actor.getFullName(), comment);
    }
}
