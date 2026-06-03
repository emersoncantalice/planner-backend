package com.planner.backend.project.periods;

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
@RequestMapping("/api/periods")
@CrossOrigin
public class PeriodsController {
    private static final Logger log = LoggerFactory.getLogger(PeriodsController.class);
    private final PeriodsService periodsService;

    public PeriodsController(PeriodsService periodsService) {
        this.periodsService = periodsService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    // ── Periods CRUD ──────────────────────────────────────────────────────────

    @GetMapping
    public List<PeriodRecord> list() throws IOException {
        return periodsService.listPeriods();
    }

    @PostMapping
    public PeriodRecord create(@RequestBody CreatePeriodRequest req, HttpServletRequest request) throws IOException {
        return periodsService.createPeriod(req, username(request));
    }

    @PutMapping("/{id}")
    public PeriodRecord update(@PathVariable String id,
                                @RequestBody CreatePeriodRequest req,
                                HttpServletRequest request) throws IOException {
        return periodsService.updatePeriod(id, req, username(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) throws IOException {
        periodsService.deletePeriod(id);
        return ResponseEntity.noContent().build();
    }

    // ── Checks ────────────────────────────────────────────────────────────────

    @GetMapping("/checks")
    public List<PeriodCheckRecord> listChecks() throws IOException {
        return periodsService.listChecks();
    }

    @PutMapping("/{periodId}/check")
    public PeriodCheckRecord check(@PathVariable String periodId,
                                    @RequestParam int ano,
                                    @RequestParam int mes,
                                    HttpServletRequest req) throws IOException {
        return periodsService.upsertCheck(periodId, username(req), ano, mes);
    }

    @DeleteMapping("/{periodId}/check")
    public ResponseEntity<Void> uncheck(@PathVariable String periodId,
                                         @RequestParam int ano,
                                         @RequestParam int mes,
                                         HttpServletRequest req) throws IOException {
        periodsService.removeCheck(periodId, username(req), ano, mes);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em periods", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend."));
    }
}
