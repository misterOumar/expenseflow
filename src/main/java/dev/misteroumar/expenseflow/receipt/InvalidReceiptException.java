package dev.misteroumar.expenseflow.receipt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Fichier refusé à l'upload : vide (400), trop volumineux (413) ou de format non autorisé (415).
 */
public class InvalidReceiptException extends ErrorResponseException {

    public InvalidReceiptException(HttpStatus status, String detail) {
        super(status, problem(status, detail), null);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("Justificatif refusé");
        return problem;
    }
}
