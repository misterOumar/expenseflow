package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.common.error.BusinessConflictException;
import dev.misteroumar.expenseflow.common.error.ResourceNotFoundException;
import dev.misteroumar.expenseflow.receipt.Receipt;
import dev.misteroumar.expenseflow.receipt.ReceiptRepository;
import dev.misteroumar.expenseflow.report.dto.LineRequest;
import dev.misteroumar.expenseflow.report.dto.ReportDetailResponse;
import dev.misteroumar.expenseflow.report.dto.ReportRequest;
import dev.misteroumar.expenseflow.report.dto.ReportSummaryResponse;
import dev.misteroumar.expenseflow.report.dto.StatusHistoryResponse;
import dev.misteroumar.expenseflow.user.User;
import dev.misteroumar.expenseflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des notes de frais en brouillon et de leurs lignes.
 * <p>
 * Toutes les opérations sont limitées aux notes dont l'utilisateur courant est propriétaire.
 * Une note d'un autre utilisateur est traitée comme inexistante (404).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ExpenseReportRepository reportRepository;
    private final StatusHistoryRepository historyRepository;
    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final ReportMapper mapper;

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> listMine(Long userId) {
        return mapper.toSummaries(reportRepository.findByOwnerIdOrderByPeriodYearDescPeriodMonthDesc(userId));
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse get(Long userId, Long reportId) {
        return mapper.toDetail(loadOwnedReport(userId, reportId));
    }

    /**
     * Crée un brouillon pour la période demandée.
     *
     * @throws BusinessConflictException si l'utilisateur a déjà une note sur cette période
     */
    public ReportDetailResponse create(Long userId, ReportRequest request) {
        User owner = loadUser(userId);
        ensurePeriodAvailable(userId, request.periodMonth(), request.periodYear());
        ExpenseReport report = new ExpenseReport(owner, request.title(), request.periodMonth(), request.periodYear());
        return mapper.toDetail(reportRepository.save(report));
    }

    /**
     * Modifie le titre et la période d'un brouillon.
     *
     * @throws BusinessConflictException si la note n'est pas en DRAFT ou si la nouvelle période est déjà prise
     */
    public ReportDetailResponse update(Long userId, Long reportId, ReportRequest request) {
        ExpenseReport report = loadOwnedReport(userId, reportId);
        boolean periodChanged = report.getPeriodMonth() != request.periodMonth()
                || report.getPeriodYear() != request.periodYear();
        if (periodChanged) {
            ensurePeriodAvailable(userId, request.periodMonth(), request.periodYear());
        }
        report.updateDetails(request.title(), request.periodMonth(), request.periodYear());
        return mapper.toDetail(report);
    }

    /**
     * Supprime un brouillon et ses lignes.
     *
     * @throws BusinessConflictException si la note n'est pas en DRAFT
     */
    public void delete(Long userId, Long reportId) {
        ExpenseReport report = loadOwnedReport(userId, reportId);
        report.ensureDraft();
        reportRepository.delete(report);
    }

    public ReportDetailResponse addLine(Long userId, Long reportId, LineRequest request) {
        ExpenseReport report = loadOwnedReport(userId, reportId);
        report.addLine(request.spentOn(), request.category(), request.description(),
                request.amountHt(), request.vatAmount(), loadReceipt(userId, request.receiptId()));
        reportRepository.flush();
        return mapper.toDetail(report);
    }

    public ReportDetailResponse updateLine(Long userId, Long reportId, Long lineId, LineRequest request) {
        ExpenseReport report = loadOwnedReport(userId, reportId);
        report.updateLine(lineId, request.spentOn(), request.category(), request.description(),
                request.amountHt(), request.vatAmount(), loadReceipt(userId, request.receiptId()));
        return mapper.toDetail(report);
    }

    public ReportDetailResponse removeLine(Long userId, Long reportId, Long lineId) {
        ExpenseReport report = loadOwnedReport(userId, reportId);
        report.removeLine(lineId);
        return mapper.toDetail(report);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(Long userId, Long reportId) {
        ExpenseReport report = loadOwnedReport(userId, reportId);
        return mapper.toHistories(historyRepository.findByReportIdOrderByAtAsc(report.getId()));
    }

    private ExpenseReport loadOwnedReport(Long userId, Long reportId) {
        User user = loadUser(userId);
        return reportRepository.findById(reportId)
                .filter(report -> report.isOwnedBy(user))
                .orElseThrow(() -> new ResourceNotFoundException("Note " + reportId + " introuvable."));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur " + userId + " introuvable."));
    }

    private Receipt loadReceipt(Long userId, Long receiptId) {
        if (receiptId == null) {
            return null;
        }
        return receiptRepository.findById(receiptId)
                .filter(receipt -> receipt.getUploadedBy().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Justificatif " + receiptId + " introuvable."));
    }

    private void ensurePeriodAvailable(Long userId, int month, int year) {
        if (reportRepository.existsByOwnerIdAndPeriodMonthAndPeriodYear(userId, month, year)) {
            throw new BusinessConflictException(
                    "Une note existe déjà pour la période " + String.format("%02d/%d", month, year) + ".");
        }
    }
}