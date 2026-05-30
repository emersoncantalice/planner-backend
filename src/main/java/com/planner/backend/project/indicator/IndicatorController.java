package com.planner.backend.project.indicator;

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
public class IndicatorController {
    private static final Logger log = LoggerFactory.getLogger(IndicatorController.class);
    private final IndicatorService indicatorService;

    public IndicatorController(IndicatorService indicatorService) {
        this.indicatorService = indicatorService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    private static String role(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_ROLE);
        return v != null ? v.toString() : "USER";
    }

    // ── Indicators ────────────────────────────────────────────────────────────

    @GetMapping("/indicators")
    public List<Indicator> listIndicators() throws IOException {
        return indicatorService.listIndicators();
    }

    @PostMapping("/indicators")
    public Indicator createIndicator(@RequestBody CreateIndicatorRequest request,
                                      HttpServletRequest req) throws IOException {
        return indicatorService.createIndicator(request, username(req));
    }

    @PutMapping("/indicators/{indicatorId}")
    public Indicator updateIndicator(@PathVariable String indicatorId,
                                      @RequestBody CreateIndicatorRequest request) throws IOException {
        return indicatorService.updateIndicator(indicatorId, request);
    }

    @DeleteMapping("/indicators/{indicatorId}")
    public ResponseEntity<Void> deleteIndicator(@PathVariable String indicatorId) throws IOException {
        indicatorService.deleteIndicator(indicatorId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/indicators/{indicatorId}/dono")
    public Indicator transferIndicatorDono(@PathVariable String indicatorId,
                                            @RequestBody TransferOwnershipRequest request,
                                            HttpServletRequest req) throws IOException {
        return indicatorService.transferIndicatorDono(indicatorId, request.novoDono(), username(req), role(req));
    }

    // ── Cycles ────────────────────────────────────────────────────────────────

    @PostMapping("/indicators/{indicatorId}/cycles")
    public Indicator addIndicatorCycle(@PathVariable String indicatorId,
                                        @RequestBody CreateIndicatorCycleRequest request) throws IOException {
        return indicatorService.addIndicatorCycle(indicatorId, request);
    }

    @PutMapping("/indicators/{indicatorId}/cycles/{cycleId}")
    public Indicator updateIndicatorCycle(@PathVariable String indicatorId, @PathVariable String cycleId,
                                           @RequestBody UpdateIndicatorCycleRequest request) throws IOException {
        return indicatorService.updateIndicatorCycle(indicatorId, cycleId, request);
    }

    @DeleteMapping("/indicators/{indicatorId}/cycles/{cycleId}")
    public Indicator deleteIndicatorCycle(@PathVariable String indicatorId,
                                           @PathVariable String cycleId) throws IOException {
        return indicatorService.deleteIndicatorCycle(indicatorId, cycleId);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @PostMapping("/indicators/{indicatorId}/actions")
    public Indicator addIndicatorAction(@PathVariable String indicatorId,
                                         @RequestBody CreateIndicatorActionRequest request) throws IOException {
        return indicatorService.addIndicatorAction(indicatorId, request);
    }

    @PutMapping("/indicators/{indicatorId}/actions/{actionId}")
    public Indicator updateIndicatorAction(@PathVariable String indicatorId, @PathVariable String actionId,
                                            @RequestBody UpdateIndicatorActionRequest request) throws IOException {
        return indicatorService.updateIndicatorAction(indicatorId, actionId, request);
    }

    @DeleteMapping("/indicators/{indicatorId}/actions/{actionId}")
    public Indicator deleteIndicatorAction(@PathVariable String indicatorId,
                                            @PathVariable String actionId) throws IOException {
        return indicatorService.deleteIndicatorAction(indicatorId, actionId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de indicators", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
