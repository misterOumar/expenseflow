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
import dev.misteroumar.expenseflow.user.Role;
import dev.misteroumar.expenseflow.user.User;
import dev.misteroumar.expenseflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Gestion des notes de frais : brouillons, lignes et transitions du workflow.
 * <p>
 * La modification d'un brouillon est réservée au propriétaire. La consultation et les transitions
 * sont ouvertes aux notes visibles (voir {@link #loadVisibleReport}). Une note non visible est
 * traitée comme inexistante (404).
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

    /**
     * Liste les notes visibles selon le rôle : un employé voit les siennes, un manager y ajoute
     * celles de son équipe, un comptable celles qui sont approuvées ou payées.
     *
     * @param status filtre optionnel sur le statut
     */
    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> list(Long userId, ReportStatus status) {
        User user = loadUser(userId);
        return mapper.toSummaries(
                reportRepository.findVisible(user.getId(), user.getRole() == Role.ACCOUNTANT, status));
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse get(Long userId, Long reportId) {
        return mapper.toDetail(loadVisibleReport(loadUser(userId), reportId));
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
        ExpenseReport report = loadVisibleReport(loadUser(userId), reportId);
        return mapper.toHistories(historyRepository.findByReportIdOrderByAtAsc(report.getId()));
    }

    /**
     * DRAFT → SUBMITTED, par le propriétaire.
     *
     * @throws AccessDeniedException     si l'utilisateur n'est pas le propriétaire
     * @throws BusinessConflictException si la note n'est pas en DRAFT, est vide ou a une ligne sans justificatif
     */
    public ReportDetailResponse submit(Long userId, Long reportId) {
        return transition(userId, reportId, this::ensureOwner, (report, actor) -> report.submit(), null);
    }

    /**
     * SUBMITTED → APPROVED, par le manager du propriétaire.
     *
     * @throws AccessDeniedException     si l'utilisateur n'est pas le manager du propriétaire
     * @throws BusinessConflictException si la note n'est pas en SUBMITTED
     */
    public ReportDetailResponse approve(Long userId, Long reportId) {
        return transition(userId, reportId, this::ensureManagerOfOwner, ExpenseReport::approve, null);
    }

    /**
     * SUBMITTED → REJECTED, par le manager du propriétaire. Le motif est repris dans l'historique.
     *
     * @throws AccessDeniedException     si l'utilisateur n'est pas le manager du propriétaire
     * @throws BusinessConflictException si la note n'est pas en SUBMITTED
     */
    public ReportDetailResponse reject(Long userId, Long reportId, String reason) {
        return transition(userId, reportId, this::ensureManagerOfOwner,
                (report, actor) -> report.reject(actor, reason), reason);
    }

    /**
     * APPROVED → PAID, par un comptable.
     *
     * @throws AccessDeniedException     si l'utilisateur n'est pas comptable
     * @throws BusinessConflictException si la note n'est pas en APPROVED
     */
    public ReportDetailResponse pay(Long userId, Long reportId) {
        return transition(userId, reportId, this::ensureAccountant, (report, actor) -> report.pay(), null);
    }

    /**
     * REJECTED → DRAFT, par le propriétaire.
     *
     * @throws AccessDeniedException     si l'utilisateur n'est pas le propriétaire
     * @throws BusinessConflictException si la note n'est pas en REJECTED
     */
    public ReportDetailResponse reopen(Long userId, Long reportId) {
        return transition(userId, reportId, this::ensureOwner, (report, actor) -> report.reopen(), null);
    }

    /**
     * Enchaîne les trois gardes (visibilité 404, acteur 403, état 409) puis trace la transition.
     */
    private ReportDetailResponse transition(Long userId, Long reportId, ActorCheck actorCheck,
                                            BiConsumer<ExpenseReport, User> action, String comment) {
        User actor = loadUser(userId);
        ExpenseReport report = loadVisibleReport(actor, reportId);
        actorCheck.verify(actor, report);
        ReportStatus from = report.getStatus();
        action.accept(report, actor);
        historyRepository.save(new StatusHistory(report, from, report.getStatus(), actor, comment));
        return mapper.toDetail(report);
    }

    @FunctionalInterface
    private interface ActorCheck {
        void verify(User actor, ExpenseReport report);
    }

    private void ensureOwner(User actor, ExpenseReport report) {
        if (!report.isOwnedBy(actor)) {
            throw new AccessDeniedException("Seul le propriétaire peut effectuer cette action.");
        }
    }

    private void ensureManagerOfOwner(User actor, ExpenseReport report) {
        if (!report.isManagedBy(actor)) {
            throw new AccessDeniedException("Seul le manager du propriétaire peut statuer sur cette note.");
        }
    }

    private void ensureAccountant(User actor, ExpenseReport report) {
        if (actor.getRole() != Role.ACCOUNTANT) {
            throw new AccessDeniedException("Seul un comptable peut marquer une note comme payée.");
        }
    }

    /**
     * Charge une note visible par l'utilisateur (voir {@link ExpenseReport#isVisibleTo}).
     * Sinon elle est traitée comme inexistante.
     */
    private ExpenseReport loadVisibleReport(User user, Long reportId) {
        return reportRepository.findById(reportId)
                .filter(report -> report.isVisibleTo(user))
                .orElseThrow(() -> new ResourceNotFoundException("Note " + reportId + " introuvable."));
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