package dev.misteroumar.expenseflow.report;

public enum ReportStatus {
    DRAFT, SUBMITTED, APPROVED, REJECTED, PAID;

    /**
     * Graphe des transitions autorisées (§4.3). Le switch est exhaustif, sans {@code default} :
     * l'ajout d'un statut oblige à décider de ses transitions.
     */
    public boolean canTransitionTo(ReportStatus target) {
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == PAID;
            case REJECTED -> target == DRAFT;
            case PAID -> false;
        };
    }
}
