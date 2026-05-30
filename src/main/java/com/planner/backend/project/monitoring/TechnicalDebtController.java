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
public class TechnicalDebtController {
    private static final Logger log = LoggerFactory.getLogger(TechnicalDebtController.class);
    private final TechnicalDebtService technicalDebtService;

    public TechnicalDebtController(TechnicalDebtService technicalDebtService) {
        this.technicalDebtService = technicalDebtService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    private static String role(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_ROLE);
        return v != null ? v.toString() : "USER";
    }

    @GetMapping("/technical-debts")
    public List<TechnicalDebt> listTechnicalDebts() throws IOException {
        return technicalDebtService.listTechnicalDebts();
    }

    @PostMapping("/technical-debts")
    public TechnicalDebt createTechnicalDebt(@RequestBody CreateTechnicalDebtRequest request,
                                              HttpServletRequest req) throws IOException {
        return technicalDebtService.createTechnicalDebt(request, username(req));
    }

    @PutMapping("/technical-debts/{debtId}")
    public TechnicalDebt updateTechnicalDebt(@PathVariable String debtId,
                                              @RequestBody CreateTechnicalDebtRequest request) throws IOException {
        return technicalDebtService.updateTechnicalDebt(debtId, request);
    }

    @DeleteMapping("/technical-debts/{debtId}")
    public ResponseEntity<Void> deleteTechnicalDebt(@PathVariable String debtId) throws IOException {
        technicalDebtService.deleteTechnicalDebt(debtId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/technical-debts/{debtId}/dono")
    public TechnicalDebt transferTechnicalDebtDono(@PathVariable String debtId,
                                                    @RequestBody TransferOwnershipRequest request,
                                                    HttpServletRequest req) throws IOException {
        return technicalDebtService.transferTechnicalDebtDono(debtId, request.novoDono(), username(req), role(req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de technical-debts", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
