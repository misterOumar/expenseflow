package dev.misteroumar.expenseflow.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration des jetons JWT ({@code expenseflow.jwt.*}).
 *
 * @param secret clé HMAC encodée en Base64, 256 bits minimum
 * @param ttl    durée de validité d'un jeton
 */
@Validated
@ConfigurationProperties(prefix = "expenseflow.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration ttl) {
}