package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
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

    public void updateDetails(String title, int periodMonth, int periodYear) {
        this.title = title;
        this.periodMonth = periodMonth;
        this.periodYear = periodYear;
    }

    public void addLine(ExpenseLine line) {
        lines.add(line);
        line.attachTo(this);
        recomputeTotal();
    }

    public void removeLine(ExpenseLine line) {
        lines.remove(line);
        line.attachTo(null);
        recomputeTotal();
    }

    public void recomputeTotal() {
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