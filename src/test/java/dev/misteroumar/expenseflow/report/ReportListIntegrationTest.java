package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportListIntegrationTest extends IntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long aliceDraft;
    private long bobSubmitted;
    private long chloeApproved;
    private long managerDraft;

    @BeforeEach
    void createReports() throws Exception {
        aliceDraft = createDraft(ALICE, 1);
        bobSubmitted = submittedReport(BOB, 1);
        chloeApproved = approvedReport(CHLOE, 1);
        managerDraft = createDraft(MANAGER, 1);
    }

    @Test
    @DisplayName("un employé ne voit que ses propres notes")
    void employeeSeesOwnReports() throws Exception {
        mvc.perform(get("/api/reports").with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder((int) aliceDraft)));
    }

    @Test
    @DisplayName("un manager voit ses notes et celles de son équipe")
    void managerSeesTeamReports() throws Exception {
        mvc.perform(get("/api/reports").with(as(MANAGER)))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(
                        (int) aliceDraft, (int) bobSubmitted, (int) chloeApproved, (int) managerDraft)));
    }

    @Test
    @DisplayName("un manager filtre les notes en attente de validation")
    void managerFiltersByStatus() throws Exception {
        mvc.perform(get("/api/reports").param("status", "SUBMITTED").with(as(MANAGER)))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder((int) bobSubmitted)));
    }

    @Test
    @DisplayName("un comptable ne voit que les notes approuvées ou payées")
    void accountantSeesApprovedReports() throws Exception {
        mvc.perform(get("/api/reports").with(as(ACCOUNTANT)))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder((int) chloeApproved)));
    }

    @Test
    @DisplayName("400 pour un statut inconnu")
    void rejectsUnknownStatus() throws Exception {
        mvc.perform(get("/api/reports").param("status", "UNKNOWN").with(as(MANAGER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("le nombre de requêtes ne dépend pas du nombre de notes (pas de N+1)")
    void listDoesNotTriggerNPlusOne() throws Exception {
        RequestPostProcessor manager = as(MANAGER);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mvc.perform(get("/api/reports").with(manager))
                .andExpect(jsonPath("$", hasSize(4)));

        // Chargement de l'utilisateur courant + requête de liste avec jointure sur le propriétaire.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
