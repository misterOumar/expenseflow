package dev.misteroumar.expenseflow.common.error;

/**
 * Opération incompatible avec l'état courant d'une ressource. Traduite en 409.
 */
public class BusinessConflictException extends RuntimeException {

    public BusinessConflictException(String message) {
        super(message);
    }
}