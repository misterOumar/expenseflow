package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.common.error.BusinessConflictException;
import dev.misteroumar.expenseflow.common.error.ResourceNotFoundException;
import dev.misteroumar.expenseflow.receipt.Receipt;
import dev.misteroumar.expenseflow.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "expense_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("spentOn ASC, id ASC")
    private List<ExpenseLine> lines = new ArrayList<>();

    public ExpenseReport(User owner, String title, int periodMonth, int periodYear) {
        this.owner = owner;
        this.title = title;
        this.periodMonth = periodMonth;
        this.periodYear = periodYear;
        this.status = ReportStatus.DRAFT;
        this.totalAmount = BigDecimal.ZERO;
    }

    public List<ExpenseLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isOwnedBy(User user) {
        return owner.getId().equals(user.getId());
    }

    public void updateDetails(String title, int periodMonth, int periodYear) {
        ensureDraft();
        this.title = title;
        this.periodMonth = periodMonth;
        this.periodYear = periodYear;
    }

    public ExpenseLine addLine(LocalDate spentOn, ExpenseCategory category, String description,
                               BigDecimal amountHt, BigDecimal vatAmount, Receipt receipt) {
        ensureDraft();
        ExpenseLine line = new ExpenseLine(spentOn, category, description, amountHt, vatAmount, receipt);
        lines.add(line);
        line.attachTo(this);
        recomputeTotal();
        return line;
    }

    public void updateLine(Long lineId, LocalDate spentOn, ExpenseCategory category, String description,
                           BigDecimal amountHt, BigDecimal vatAmount, Receipt receipt) {
        ensureDraft();
        findLine(lineId).update(spentOn, category, description, amountHt, vatAmount, receipt);
        recomputeTotal();
    }

    public void removeLine(Long lineId) {
        ensureDraft();
        ExpenseLine line = findLine(lineId);
        lines.remove(line);
        line.attachTo(null);
        recomputeTotal();
    }

    /**
     * Garantit qu'une note n'est modifiable qu'en statut DRAFT (§4.3).
     *
     * @throws BusinessConflictException si la note n'est pas en brouillon
     */
    public void ensureDraft() {
        if (status != ReportStatus.DRAFT) {
            throw new BusinessConflictException(
                    "La note " + id + " est en statut " + status + " : seul un brouillon est modifiable.");
        }
    }

    private ExpenseLine findLine(Long lineId) {
        return lines.stream()
                .filter(line -> line.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ligne " + lineId + " introuvable dans la note " + id + "."));
    }

    private void recomputeTotal() {
        this.totalAmount = lines.stream()
                .map(ExpenseLine::getAmountTtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseReport other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}