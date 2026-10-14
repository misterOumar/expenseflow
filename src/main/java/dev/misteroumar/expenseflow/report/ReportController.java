package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.report.dto.LineRequest;
import dev.misteroumar.expenseflow.report.dto.RejectRequest;
import dev.misteroumar.expenseflow.security.CurrentUserId;
import dev.misteroumar.expenseflow.report.dto.ReportDetailResponse;
import dev.misteroumar.expenseflow.report.dto.ReportRequest;
import dev.misteroumar.expenseflow.report.dto.ReportSummaryResponse;
import dev.misteroumar.expenseflow.report.dto.StatusHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de gestion des notes de frais et de leur workflow, réservés à l'utilisateur authentifié.
 */
@Tag(name = "Notes de frais", description = "Brouillons, lignes et transitions de statut")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Lister les notes visibles",
            description = "Employé : ses notes. Manager : les siennes et celles de son équipe. "
                    + "Comptable : les notes APPROVED et PAID. Filtre optionnel par statut.")
    @GetMapping
    public List<ReportSummaryResponse> list(@CurrentUserId Long userId,
                                            @RequestParam(required = false) ReportStatus status) {
        return reportService.list(userId, status);
    }

    @Operation(summary = "Créer un brouillon", description = "409 si une note existe déjà pour la période.")
    @PostMapping
    public ResponseEntity<ReportDetailResponse> create(@CurrentUserId Long userId,
                                                       @Valid @RequestBody ReportRequest request) {
        ReportDetailResponse created = reportService.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Consulter une note avec ses lignes")
    @GetMapping("/{id}")
    public ReportDetailResponse get(@CurrentUserId Long userId, @PathVariable Long id) {
        return reportService.get(userId, id);
    }

    @Operation(summary = "Modifier un brouillon", description = "409 si la note n'est pas en DRAFT.")
    @PutMapping("/{id}")
    public ReportDetailResponse update(@CurrentUserId Long userId, @PathVariable Long id,
                                       @Valid @RequestBody ReportRequest request) {
        return reportService.update(userId, id, request);
    }

    @Operation(summary = "Supprimer un brouillon", description = "409 si la note n'est pas en DRAFT.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long id) {
        reportService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ajouter une ligne", description = "Le montant TTC et le total de la note sont recalculés.")
    @PostMapping("/{id}/lines")
    public ResponseEntity<ReportDetailResponse> addLine(@CurrentUserId Long userId,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody LineRequest request) {
        return ResponseEntity.status(201).body(reportService.addLine(userId, id, request));
    }

    @Operation(summary = "Modifier une ligne")
    @PutMapping("/{id}/lines/{lineId}")
    public ReportDetailResponse updateLine(@CurrentUserId Long userId, @PathVariable Long id,
                                           @PathVariable Long lineId, @Valid @RequestBody LineRequest request) {
        return reportService.updateLine(userId, id, lineId, request);
    }

    @Operation(summary = "Supprimer une ligne")
    @DeleteMapping("/{id}/lines/{lineId}")
    public ReportDetailResponse removeLine(@CurrentUserId Long userId, @PathVariable Long id,
                                           @PathVariable Long lineId) {
        return reportService.removeLine(userId, id, lineId);
    }

    @Operation(summary = "Historique des changements de statut")
    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@CurrentUserId Long userId, @PathVariable Long id) {
        return reportService.history(userId, id);
    }

    @Operation(summary = "Soumettre une note", description = "Propriétaire. 409 si la note est vide ou si une ligne n'a pas de justificatif.")
    @PostMapping("/{id}/submit")
    public ReportDetailResponse submit(@CurrentUserId Long userId, @PathVariable Long id) {
        return reportService.submit(userId, id);
    }

    @Operation(summary = "Approuver une note", description = "Manager du propriétaire. 409 si la note n'est pas en SUBMITTED.")
    @PostMapping("/{id}/approve")
    public ReportDetailResponse approve(@CurrentUserId Long userId, @PathVariable Long id) {
        return reportService.approve(userId, id);
    }

    @Operation(summary = "Rejeter une note", description = "Manager du propriétaire. Motif de 10 caractères minimum.")
    @PostMapping("/{id}/reject")
    public ReportDetailResponse reject(@CurrentUserId Long userId, @PathVariable Long id,
                                       @Valid @RequestBody RejectRequest request) {
        return reportService.reject(userId, id, request.reason());
    }

    @Operation(summary = "Marquer une note comme payée", description = "Comptable. 409 si la note n'est pas en APPROVED.")
    @PostMapping("/{id}/pay")
    public ReportDetailResponse pay(@CurrentUserId Long userId, @PathVariable Long id) {
        return reportService.pay(userId, id);
    }

    @Operation(summary = "Rouvrir une note rejetée", description = "Propriétaire. Repasse la note en DRAFT.")
    @PostMapping("/{id}/reopen")
    public ReportDetailResponse reopen(@CurrentUserId Long userId, @PathVariable Long id) {
        return reportService.reopen(userId, id);
    }
}