package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.receipt.Receipt;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private ExpenseReport report;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ExpenseCategory category;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount_ht", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountHt;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "amount_ttc", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountTtc;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", unique = true)
    private Receipt receipt;

    public ExpenseLine(LocalDate spentOn, ExpenseCategory category, String description,
                       BigDecimal amountHt, BigDecimal vatAmount) {
        update(spentOn, category, description, amountHt, vatAmount);
    }

    public void update(LocalDate spentOn, ExpenseCategory category, String description,
                       BigDecimal amountHt, BigDecimal vatAmount) {
        this.spentOn = spentOn;
        this.category = category;
        this.description = description;
        this.amountHt = amountHt;
        this.vatAmount = vatAmount;
        this.amountTtc = amountHt.add(vatAmount);
    }

    public void attachReceipt(Receipt receipt) {
        this.receipt = receipt;
    }

    // Appelé uniquement par ExpenseReport.addLine/removeLine pour garder les deux côtés synchronisés.
    void attachTo(ExpenseReport report) {
        this.report = report;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseLine other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}