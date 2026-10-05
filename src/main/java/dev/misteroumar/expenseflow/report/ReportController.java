package dev.misteroumar.expenseflow.report;

import dev.misteroumar.expenseflow.report.dto.LineRequest;
import dev.misteroumar.expenseflow.report.dto.ReportDetailResponse;
import dev.misteroumar.expenseflow.report.dto.ReportRequest;
import dev.misteroumar.expenseflow.report.dto.ReportSummaryResponse;
import dev.misteroumar.expenseflow.report.dto.StatusHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de gestion des notes de frais en brouillon.
 * <p>
 * L'utilisateur courant est identifié provisoirement par l'en-tête {@code X-User-Id},
 * en attendant l'authentification JWT.
 */
@Tag(name = "Notes de frais", description = "Création et modification des brouillons")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final String USER_HEADER = "X-User-Id";

    private final ReportService reportService;

    @Operation(summary = "Lister mes notes")
    @GetMapping
    public List<ReportSummaryResponse> list(@Parameter(description = "Utilisateur courant (provisoire)")
                                            @RequestHeader(USER_HEADER) Long userId) {
        return reportService.listMine(userId);
    }

    @Operation(summary = "Créer un brouillon", description = "409 si une note existe déjà pour la période.")
    @PostMapping
    public ResponseEntity<ReportDetailResponse> create(@RequestHeader(USER_HEADER) Long userId,
                                                       @Valid @RequestBody ReportRequest request) {
        ReportDetailResponse created = reportService.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Consulter une note avec ses lignes")
    @GetMapping("/{id}")
    public ReportDetailResponse get(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        return reportService.get(userId, id);
    }

    @Operation(summary = "Modifier un brouillon", description = "409 si la note n'est pas en DRAFT.")
    @PutMapping("/{id}")
    public ReportDetailResponse update(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id,
                                       @Valid @RequestBody ReportRequest request) {
        return reportService.update(userId, id, request);
    }

    @Operation(summary = "Supprimer un brouillon", description = "409 si la note n'est pas en DRAFT.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        reportService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ajouter une ligne", description = "Le montant TTC et le total de la note sont recalculés.")
    @PostMapping("/{id}/lines")
    public ResponseEntity<ReportDetailResponse> addLine(@RequestHeader(USER_HEADER) Long userId,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody LineRequest request) {
        return ResponseEntity.status(201).body(reportService.addLine(userId, id, request));
    }

    @Operation(summary = "Modifier une ligne")
    @PutMapping("/{id}/lines/{lineId}")
    public ReportDetailResponse updateLine(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id,
                                           @PathVariable Long lineId, @Valid @RequestBody LineRequest request) {
        return reportService.updateLine(userId, id, lineId, request);
    }

    @Operation(summary = "Supprimer une ligne")
    @DeleteMapping("/{id}/lines/{lineId}")
    public ReportDetailResponse removeLine(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id,
                                           @PathVariable Long lineId) {
        return reportService.removeLine(userId, id, lineId);
    }

    @Operation(summary = "Historique des changements de statut")
    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        return reportService.history(userId, id);
    }
}