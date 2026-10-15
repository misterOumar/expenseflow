package dev.misteroumar.expenseflow.notification;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration des notifications ({@code expenseflow.notification.*}). Le serveur SMTP
 * se configure via {@code spring.mail.*}.
 *
 * @param from expéditeur des emails
 */
@Validated
@ConfigurationProperties(prefix = "expenseflow.notification")
public record NotificationProperties(@NotBlank String from) {
}
