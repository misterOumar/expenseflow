package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowIntegrationTest extends IntegrationTest {

    @Nested
    @DisplayName("DRAFT → SUBMITTED")
    class Submit {

        @Test
        @DisplayName("le propriétaire soumet une note complète")
        void ownerSubmits() throws Exception {
            long id = createDraft(ALICE, 1);
            addLine(ALICE, id, true);

            mvc.perform(post("/api/reports/{id}/submit", id).with(as(ALICE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.submittedAt").isNotEmpty());
        }

        @Test
        @DisplayName("409 si la note n'a aucune ligne")
        void rejectsEmptyReport() throws Exception {
            long id = createDraft(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/submit", id).with(as(ALICE)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("409 si une ligne n'a pas de justificatif")
        void rejectsLineWithoutReceipt() throws Exception {
            long id = createDraft(ALICE, 1);
            addLine(ALICE, id, true);
            addLine(ALICE, id, false);

            mvc.perform(post("/api/reports/{id}/submit", id).with(as(ALICE)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("403 si le manager soumet à la place du propriétaire")
        void forbidsNonOwner() throws Exception {
            long id = createDraft(ALICE, 1);
            addLine(ALICE, id, true);

            mvc.perform(post("/api/reports/{id}/submit", id).with(as(MANAGER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("409 si la note est modifiée après soumission")
        void lockedOnceSubmitted() throws Exception {
            long id = submittedReport(ALICE, 1);

            mvc.perform(put("/api/reports/{id}", id).with(as(ALICE))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "Autre titre", "periodMonth": 1, "periodYear": 2026}
                                    """))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("SUBMITTED → APPROVED")
    class Approve {

        @Test
        @DisplayName("le manager du propriétaire approuve")
        void managerApproves() throws Exception {
            long id = submittedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/approve", id).with(as(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.decidedByName").value("Claire Martin"));
        }

        @Test
        @DisplayName("403 si le propriétaire s'approuve lui-même")
        void ownerCannotApprove() throws Exception {
            long id = submittedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/approve", id).with(as(ALICE)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("403 si un manager approuve sa propre note")
        void managerCannotApproveOwnReport() throws Exception {
            long id = submittedReport(MANAGER, 1);

            mvc.perform(post("/api/reports/{id}/approve", id).with(as(MANAGER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("409 si la note est encore en brouillon")
        void rejectsDraft() throws Exception {
            long id = createDraft(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/approve", id).with(as(MANAGER)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("409 si la note est déjà approuvée")
        void rejectsDoubleApproval() throws Exception {
            long id = approvedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/approve", id).with(as(MANAGER)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("SUBMITTED → REJECTED")
    class Reject {

        @Test
        @DisplayName("le manager rejette avec un motif, repris dans l'historique")
        void managerRejects() throws Exception {
            long id = submittedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/reject", id).with(as(MANAGER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reason": "Justificatif illisible"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value("Justificatif illisible"));

            mvc.perform(get("/api/reports/{id}/history", id).with(as(ALICE)))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[1].toStatus").value("REJECTED"))
                    .andExpect(jsonPath("$[1].comment").value("Justificatif illisible"));
        }

        @Test
        @DisplayName("400 si le motif fait moins de 10 caractères")
        void requiresReason() throws Exception {
            long id = submittedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/reject", id).with(as(MANAGER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reason": "court"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("409 si la note est déjà approuvée")
        void rejectsApprovedReport() throws Exception {
            long id = approvedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/reject", id).with(as(MANAGER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reason": "Changement d'avis"}
                                    """))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("REJECTED → DRAFT")
    class Reopen {

        @Test
        @DisplayName("le propriétaire rouvre une note rejetée, la décision est effacée")
        void ownerReopens() throws Exception {
            long id = submittedReport(ALICE, 1);
            mvc.perform(post("/api/reports/{id}/reject", id).with(as(MANAGER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reason": "Justificatif illisible"}
                            """));

            mvc.perform(post("/api/reports/{id}/reopen", id).with(as(ALICE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.rejectionReason").isEmpty())
                    .andExpect(jsonPath("$.decidedByName").isEmpty());
        }

        @Test
        @DisplayName("409 si la note n'est pas rejetée")
        void rejectsSubmittedReport() throws Exception {
            long id = submittedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/reopen", id).with(as(ALICE)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("APPROVED → PAID")
    class Pay {

        @Test
        @DisplayName("le comptable marque une note approuvée comme payée")
        void accountantPays() throws Exception {
            long id = approvedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/pay", id).with(as(ACCOUNTANT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"));
        }

        @Test
        @DisplayName("403 si le manager tente de payer")
        void forbidsManager() throws Exception {
            long id = approvedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/pay", id).with(as(MANAGER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("409 si la note est déjà payée")
        void rejectsDoublePayment() throws Exception {
            long id = approvedReport(ALICE, 1);
            transition(ACCOUNTANT, id, "pay");

            mvc.perform(post("/api/reports/{id}/pay", id).with(as(ACCOUNTANT)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Accès aux notes")
    class Access {

        @Test
        @DisplayName("404 quand un employé consulte la note d'un autre employé")
        void employeeCannotReadOthersReport() throws Exception {
            long id = createDraft(ALICE, 1);

            mvc.perform(get("/api/reports/{id}", id).with(as(BOB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404 quand un employé tente de statuer sur la note d'un autre")
        void employeeCannotActOnOthersReport() throws Exception {
            long id = submittedReport(ALICE, 1);

            mvc.perform(post("/api/reports/{id}/approve", id).with(as(BOB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404 quand le comptable consulte une note non approuvée")
        void accountantCannotReadSubmittedReport() throws Exception {
            long id = submittedReport(ALICE, 1);

            mvc.perform(get("/api/reports/{id}", id).with(as(ACCOUNTANT)))
                    .andExpect(status().isNotFound());
        }
    }
}
