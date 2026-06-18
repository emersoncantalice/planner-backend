package com.planner.backend.project.tracking;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.AuthTokenInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class AllocationStateController {
    private static final Logger log = LoggerFactory.getLogger(AllocationStateController.class);
    private final AllocationStateService allocationStateService;

    public AllocationStateController(AllocationStateService allocationStateService) {
        this.allocationStateService = allocationStateService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    // ── Allocation Payments ───────────────────────────────────────────────────

    @GetMapping("/allocation-payments")
    public List<AllocationPaymentState> listAllocationPayments() throws IOException {
        return allocationStateService.listAllocationPayments();
    }

    @PutMapping("/allocation-payments/{allocationId}/{month}")
    public AllocationPaymentState upsertAllocationPayment(
            @PathVariable String allocationId,
            @PathVariable int month,
            @RequestBody UpdateAllocationPaymentRequest request,
            HttpServletRequest req) throws IOException {
        boolean paid = request != null && Boolean.TRUE.equals(request.paid());
        return allocationStateService.upsertAllocationPayment(allocationId, month, paid, username(req));
    }

    // ── Lo Presence ───────────────────────────────────────────────────────────

    @GetMapping("/lo-presence")
    public List<LoPresenceState> listLoPresence() throws IOException {
        return allocationStateService.listLoPresence();
    }

    @PutMapping("/lo-presence")
    public LoPresenceState upsertLoPresence(
            @RequestBody UpsertLoPresenceRequest request,
            HttpServletRequest req) throws IOException {
        return allocationStateService.upsertLoPresence(
                request != null ? request.loId() : null, username(req));
    }

    // ── Allocation Monthly State ──────────────────────────────────────────────

    @GetMapping("/allocation-monthly-state")
    public List<AllocationMonthlyState> listAllocationMonthlyStates() throws IOException {
        return allocationStateService.listAllocationMonthlyStates();
    }

    @PutMapping("/allocation-monthly-state/{allocationId}/{month}")
    public AllocationMonthlyState upsertAllocationMonthlyState(
            @PathVariable String allocationId,
            @PathVariable int month,
            @RequestBody UpdateAllocationMonthlyStateRequest request,
            HttpServletRequest req) throws IOException {
        return allocationStateService.upsertAllocationMonthlyState(allocationId, month, request, username(req));
    }

    // ── Allocation Percent ────────────────────────────────────────────────────

    @GetMapping("/allocation-percent")
    public List<AllocationPercentConfig> listAllocationPercents() throws IOException {
        return allocationStateService.listAllocationPercents();
    }

    @PutMapping("/allocation-percent/{allocationId}")
    public AllocationPercentConfig upsertAllocationPercent(
            @PathVariable String allocationId,
            @RequestBody UpsertAllocationPercentRequest request,
            HttpServletRequest req) throws IOException {
        java.math.BigDecimal pct = request != null ? request.percentual() : null;
        return allocationStateService.upsertAllocationPercent(allocationId, pct, username(req));
    }

    // ── Allocation Notes (anotação por alocação) ───────────────────────────────

    @GetMapping("/allocation-notes")
    public List<AllocationNoteConfig> listAllocationNotes() throws IOException {
        return allocationStateService.listAllocationNotes();
    }

    @PutMapping("/allocation-notes/{allocationId}")
    public AllocationNoteConfig upsertAllocationNote(
            @PathVariable String allocationId,
            @RequestBody UpsertAllocationNoteRequest request,
            HttpServletRequest req) throws IOException {
        String nota = request != null ? request.nota() : null;
        return allocationStateService.upsertAllocationNote(allocationId, nota, username(req));
    }

    // ── Allocation Cursors ────────────────────────────────────────────────────

    @GetMapping("/allocation-cursors")
    public List<AllocationCursorState> listAllocationCursors(@RequestParam(required = false) String loId) throws IOException {
        return allocationStateService.listAllocationCursors(loId);
    }

    @PutMapping("/allocation-cursors")
    public AllocationCursorState upsertAllocationCursor(
            @RequestBody UpsertAllocationCursorRequest request,
            HttpServletRequest req) throws IOException {
        return allocationStateService.upsertAllocationCursor(request, username(req));
    }

    // ── LO Favoritos ─────────────────────────────────────────────────────────

    @GetMapping("/lo-favorites")
    public List<LoFavoriteConfig> listLoFavoritos() throws IOException {
        return allocationStateService.listLoFavoritos();
    }

    @PutMapping("/lo-favorites/{loId}")
    public List<LoFavoriteConfig> addLoFavorito(
            @PathVariable String loId,
            HttpServletRequest req) throws IOException {
        return allocationStateService.addLoFavorito(loId, username(req));
    }

    @DeleteMapping("/lo-favorites/{loId}")
    public List<LoFavoriteConfig> removeLoFavorito(
            @PathVariable String loId,
            HttpServletRequest req) throws IOException {
        return allocationStateService.removeLoFavorito(loId, username(req));
    }

    // ── Lo Realizado ──────────────────────────────────────────────────────────

    @GetMapping("/lo-realizado")
    public List<LoRealizadoConfig> listLoRealizado() throws IOException {
        return allocationStateService.listLoRealizado();
    }

    @PutMapping("/lo-realizado/{loId}/{month}")
    public LoRealizadoConfig upsertLoRealizado(
            @PathVariable String loId,
            @PathVariable int month,
            @RequestBody UpsertLoRealizadoRequest request,
            HttpServletRequest req) throws IOException {
        java.math.BigDecimal valor = request != null ? request.valor() : null;
        return allocationStateService.upsertLoRealizado(loId, month, valor, username(req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de tracking", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
