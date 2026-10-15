package dev.misteroumar.expenseflow.notification;

import dev.misteroumar.expenseflow.report.ReportStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Notifie par email les transitions vers SUBMITTED, APPROVED et REJECTED (§4.6).
 * <p>
 * {@code AFTER_COMMIT} : aucun appel réseau dans la transaction, et aucun email pour une transition
 * annulée (§7.2). {@code @Async} : un serveur SMTP lent ou en panne ne retarde ni ne fait échouer
 * la réponse HTTP, la transition étant déjà enregistrée.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(NotificationProperties.class)
public class ReportMailListener {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ReportStatusChanged event) {
        compose(event).ifPresent(message -> {
            try {
                mailSender.send(message);
                log.info("Email envoyé à {} pour la note {} ({})", message.getTo(), event.reportId(), event.to());
            } catch (MailException ex) {
                log.error("Échec d'envoi de l'email pour la note {} ({})", event.reportId(), event.to(), ex);
            }
        });
    }

    Optional<SimpleMailMessage> compose(ReportStatusChanged event) {
        return switch (event.to()) {
            case SUBMITTED -> submitted(event);
            case APPROVED -> Optional.of(message(event.ownerEmail(),
                    "Note de frais approuvée : " + event.title(),
                    """
                            Bonjour %s,

                            Votre note de frais « %s » (%s, %s) a été approuvée par %s.
                            """.formatted(event.ownerName(), event.title(), period(event),
                            amount(event), event.actorName())));
            case REJECTED -> Optional.of(message(event.ownerEmail(),
                    "Note de frais rejetée : " + event.title(),
                    """
                            Bonjour %s,

                            Votre note de frais « %s » (%s, %s) a été rejetée par %s.

                            Motif : %s

                            Vous pouvez la rouvrir pour la corriger puis la soumettre à nouveau.
                            """.formatted(event.ownerName(), event.title(), period(event),
                            amount(event), event.actorName(), event.comment())));
            case DRAFT, PAID -> Optional.empty();
        };
    }

    private Optional<SimpleMailMessage> submitted(ReportStatusChanged event) {
        if (event.managerEmail() == null) {
            log.info("Note {} soumise par {} sans manager : aucun email envoyé", event.reportId(), event.ownerName());
            return Optional.empty();
        }
        return Optional.of(message(event.managerEmail(),
                "Note de frais à valider : " + event.title(),
                """
                        Bonjour %s,

                        %s a soumis la note de frais « %s » (%s, %s).
                        Elle est en attente de votre validation.
                        """.formatted(event.managerName(), event.ownerName(), event.title(),
                        period(event), amount(event))));
    }

    private SimpleMailMessage message(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        return message;
    }

    private static String period(ReportStatusChanged event) {
        return "%02d/%d".formatted(event.periodMonth(), event.periodYear());
    }

    private static String amount(ReportStatusChanged event) {
        return NumberFormat.getCurrencyInstance(Locale.FRANCE).format(event.totalAmount());
    }
}
