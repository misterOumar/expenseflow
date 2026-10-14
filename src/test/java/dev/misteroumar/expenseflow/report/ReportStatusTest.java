package dev.misteroumar.expenseflow.report;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReportStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "DRAFT>SUBMITTED", "SUBMITTED>APPROVED", "SUBMITTED>REJECTED", "REJECTED>DRAFT", "APPROVED>PAID");

    @ParameterizedTest(name = "{0} → toutes les cibles")
    @CsvSource({"DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "PAID"})
    void onlyDocumentedTransitionsAreAllowed(ReportStatus from) {
        for (ReportStatus to : EnumSet.allOf(ReportStatus.class)) {
            assertThat(from.canTransitionTo(to))
                    .as("%s → %s", from, to)
                    .isEqualTo(ALLOWED.contains(from + ">" + to));
        }
    }
}
