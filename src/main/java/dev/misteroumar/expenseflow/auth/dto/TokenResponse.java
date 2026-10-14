package dev.misteroumar.expenseflow.auth.dto;

/**
 * @param accessToken JWT à transmettre dans l'en-tête {@code Authorization: Bearer <token>}
 * @param tokenType   toujours {@code Bearer}
 * @param expiresIn   durée de validité en secondes
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}