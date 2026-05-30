package com.planner.backend.project.monitoring;

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
public class RiskController {
    private static final Logger log = LoggerFactory.getLogger(RiskController.class);
    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    private static String role(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_ROLE);
        return v != null ? v.toString() : "USER";
    }

    @GetMapping("/risks")
    public List<GlobalRisk> listRisks() throws IOException {
        return riskService.listGlobalRisks();
    }

    @PostMapping("/risks")
    public GlobalRisk createRisk(@RequestBody CreateGlobalRiskRequest request,
                                  HttpServletRequest req) throws IOException {
        return riskService.createGlobalRisk(request, username(req));
    }

    @PostMapping("/risks/import-csv")
    public ImportCsvResponse importRisksCsv(@RequestBody ImportRisksCsvRequest request) throws IOException {
        return riskService.importGlobalRisksCsv(request);
    }

    @PatchMapping("/risks/{riskId}")
    public GlobalRisk updateRiskStatus(@PathVariable String riskId,
                                        @RequestBody UpdateGlobalRiskStatusRequest request) throws IOException {
        return riskService.updateGlobalRiskStatus(riskId, request);
    }

    @PutMapping("/risks/{riskId}")
    public GlobalRisk updateRisk(@PathVariable String riskId,
                                  @RequestBody CreateGlobalRiskRequest request) throws IOException {
        return riskService.updateGlobalRisk(riskId, request);
    }

    @PostMapping("/risks/{riskId}/postpone")
    public GlobalRisk postponeRisk(@PathVariable String riskId,
                                    @RequestBody PostponeGlobalRiskRequest request) throws IOException {
        return riskService.postponeGlobalRisk(riskId, request);
    }

    @DeleteMapping("/risks/{riskId}")
    public ResponseEntity<Void> deleteRisk(@PathVariable String riskId) throws IOException {
        riskService.deleteGlobalRisk(riskId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/risks/{riskId}/dono")
    public GlobalRisk transferRiskDono(@PathVariable String riskId,
                                        @RequestBody TransferOwnershipRequest request,
                                        HttpServletRequest req) throws IOException {
        return riskService.transferGlobalRiskDono(riskId, request.novoDono(), username(req), role(req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de risks", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
