package dev.misteroumar.expenseflow.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank @Size(min = 10, max = 1000) String reason
) {
}
