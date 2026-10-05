package dev.misteroumar.expenseflow.common.error;

/**
 * Ressource inexistante ou non accessible par l'utilisateur courant. Traduite en 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}