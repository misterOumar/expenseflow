package dev.misteroumar.expenseflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie le jeu de démonstration chargé par Flyway (V3 et V4) sur une base neuve.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DemoDataMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("3 employés, 1 manager et 1 comptable")
    void demoUsers() {
        assertThat(jdbc.queryForList("SELECT role FROM users", String.class))
                .containsExactlyInAnyOrder("EMPLOYEE", "EMPLOYEE", "EMPLOYEE", "MANAGER", "ACCOUNTANT");
    }

    @Test
    @DisplayName("au moins une note dans chaque statut")
    void everyStatusIsRepresented() {
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM expense_reports", String.class))
                .containsExactlyInAnyOrder("DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "PAID");
    }

    @Test
    @DisplayName("le total de chaque note est la somme de ses lignes")
    void totalsMatchLines() {
        assertThat(jdbc.queryForList("""
                SELECT r.title FROM expense_reports r
                WHERE r.total_amount <> (SELECT COALESCE(SUM(l.amount_ttc), 0) FROM expense_lines l WHERE l.report_id = r.id)
                """, String.class)).isEmpty();
    }

    @Test
    @DisplayName("toute note soumise au moins une fois a un justificatif sur chaque ligne")
    void submittedLinesHaveReceipts() {
        assertThat(jdbc.queryForList("""
                SELECT l.description FROM expense_lines l
                JOIN expense_reports r ON r.id = l.report_id
                WHERE l.receipt_id IS NULL
                  AND EXISTS (SELECT 1 FROM status_history h WHERE h.report_id = r.id)
                """, String.class)).isEmpty();
    }

    @Test
    @DisplayName("le dernier statut de l'historique est le statut courant de la note")
    void historyEndsOnCurrentStatus() {
        List<String> inconsistent = jdbc.queryForList("""
                SELECT r.title FROM expense_reports r
                WHERE r.status <> COALESCE(
                    (SELECT h.to_status FROM status_history h WHERE h.report_id = r.id ORDER BY h.at DESC LIMIT 1),
                    'DRAFT')
                """, String.class);
        assertThat(inconsistent).isEmpty();
    }
}
