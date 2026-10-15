package dev.misteroumar.expenseflow.notification;

import dev.misteroumar.expenseflow.IntegrationTest;
import dev.misteroumar.expenseflow.report.ReportStatus;
import dev.misteroumar.expenseflow.report.ReportStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportMailIntegrationTest extends IntegrationTest {

    private static final long ASYNC_TIMEOUT_MS = 2000;
    private static final long NO_MAIL_WAIT_MS = 500;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("la soumission notifie le manager du propriétaire")
    void submitNotifiesManager() throws Exception {
        submittedReport(ALICE, 1);

        verify(mailSender, timeout(ASYNC_TIMEOUT_MS)).send(argThat((SimpleMailMessage mail) ->
                sentTo(mail, MANAGER)
                        && mail.getSubject().startsWith("Note de frais à valider")
                        && mail.getText().contains("Alice Durand a soumis")));
    }

    @Test
    @DisplayName("l'approbation notifie le propriétaire")
    void approveNotifiesOwner() throws Exception {
        approvedReport(ALICE, 1);

        verify(mailSender, timeout(ASYNC_TIMEOUT_MS)).send(argThat((SimpleMailMessage mail) ->
                sentTo(mail, ALICE) && mail.getSubject().startsWith("Note de frais approuvée")));
    }

    @Test
    @DisplayName("le rejet notifie le propriétaire avec le motif")
    void rejectNotifiesOwnerWithReason() throws Exception {
        long id = submittedReport(ALICE, 1);

        mvc.perform(post("/api/reports/{id}/reject", id).with(as(MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "Justificatif illisible"}
                        """));

        verify(mailSender, timeout(ASYNC_TIMEOUT_MS)).send(argThat((SimpleMailMessage mail) ->
                sentTo(mail, ALICE)
                        && mail.getSubject().startsWith("Note de frais rejetée")
                        && mail.getText().contains("Motif : Justificatif illisible")));
    }

    @Test
    @DisplayName("aucun email quand la note soumise n'a pas de manager")
    void noMailWithoutManager() throws Exception {
        submittedReport(MANAGER, 1);

        verify(mailSender, after(NO_MAIL_WAIT_MS).never()).send(argThat((SimpleMailMessage mail) ->
                mail.getSubject().startsWith("Note de frais à valider")));
    }

    @Test
    @DisplayName("aucun email pour une transition refusée")
    void noMailWhenTransitionFails() throws Exception {
        long id = createDraft(ALICE, 1);

        mvc.perform(post("/api/reports/{id}/approve", id).with(as(MANAGER)))
                .andExpect(status().isConflict());

        verify(mailSender, after(NO_MAIL_WAIT_MS).never()).send(argThat((SimpleMailMessage mail) ->
                sentTo(mail, ALICE)));
    }

    @Test
    @DisplayName("un événement publié dans une transaction annulée n'envoie aucun email")
    void noMailOnRollback() {
        transactionTemplate.executeWithoutResult(tx -> {
            events.publishEvent(event("Note annulée"));
            tx.setRollbackOnly();
        });
        transactionTemplate.executeWithoutResult(tx -> events.publishEvent(event("Note validée")));

        verify(mailSender, timeout(ASYNC_TIMEOUT_MS)).send(argThat((SimpleMailMessage mail) ->
                mail.getSubject().endsWith("Note validée")));
        verify(mailSender, after(NO_MAIL_WAIT_MS).never()).send(argThat((SimpleMailMessage mail) ->
                mail.getSubject().endsWith("Note annulée")));
    }

    @Test
    @DisplayName("une panne SMTP n'empêche pas la transition")
    void smtpFailureDoesNotBreakTransition() throws Exception {
        doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(SimpleMailMessage.class));
        long id = submittedReport(ALICE, 1);

        mvc.perform(post("/api/reports/{id}/approve", id).with(as(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private boolean sentTo(SimpleMailMessage mail, String email) {
        return mail.getTo() != null && mail.getTo().length == 1 && mail.getTo()[0].equals(email);
    }

    private ReportStatusChanged event(String title) {
        return new ReportStatusChanged(0L, title, 1, 2026, new BigDecimal("12.00"),
                ReportStatus.SUBMITTED, ReportStatus.APPROVED, "Alice Durand", ALICE,
                "Claire Martin", MANAGER, "Claire Martin", null);
    }
}
