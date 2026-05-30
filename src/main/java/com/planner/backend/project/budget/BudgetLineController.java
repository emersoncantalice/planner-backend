package com.planner.backend.project.budget;

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
public class BudgetLineController {
    private static final Logger log = LoggerFactory.getLogger(BudgetLineController.class);
    private final BudgetLineService budgetLineService;

    public BudgetLineController(BudgetLineService budgetLineService) {
        this.budgetLineService = budgetLineService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    private static String role(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_ROLE);
        return v != null ? v.toString() : "USER";
    }

    // ── Budget Lines ──────────────────────────────────────────────────────────

    @GetMapping("/budget-lines")
    public List<BudgetLine> listBudgetLines(HttpServletRequest req) throws IOException {
        return budgetLineService.listBudgetLines(username(req), role(req));
    }

    @PostMapping("/budget-lines")
    public BudgetLine createBudgetLine(@RequestBody CreateBudgetLineRequest request, HttpServletRequest req) throws IOException {
        return budgetLineService.createBudgetLine(request, username(req));
    }

    @PatchMapping("/budget-lines/{budgetLineId}/situacao")
    public BudgetLine updateBudgetLineSituacao(@PathVariable String budgetLineId,
                                               @RequestBody UpdateBudgetLineSituacaoRequest request,
                                               HttpServletRequest req) throws IOException {
        return budgetLineService.updateBudgetLineSituacao(budgetLineId, request.situacao(), username(req), role(req));
    }

    @PostMapping("/budget-lines/import-csv")
    public ImportCsvResponse importBudgetLinesCsv(@RequestBody ImportBudgetLinesCsvRequest request) throws IOException {
        return budgetLineService.importBudgetLinesCsv(request);
    }

    @PutMapping("/budget-lines/{budgetLineId}")
    public BudgetLine updateBudgetLine(@PathVariable String budgetLineId, @RequestBody CreateBudgetLineRequest request) throws IOException {
        return budgetLineService.updateBudgetLine(budgetLineId, request);
    }

    @DeleteMapping("/budget-lines/{budgetLineId}")
    public ResponseEntity<Void> deleteBudgetLine(@PathVariable String budgetLineId) throws IOException {
        budgetLineService.deleteBudgetLine(budgetLineId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/budget-lines/{budgetLineId}/dono")
    public BudgetLine transferBudgetLineDono(@PathVariable String budgetLineId,
                                              @RequestBody TransferOwnershipRequest request,
                                              HttpServletRequest req) throws IOException {
        return budgetLineService.transferBudgetLineDono(budgetLineId, request.novoDono(), username(req), role(req));
    }

    // ── Budget Line Adjustments ───────────────────────────────────────────────

    @GetMapping("/budget-line-adjustments")
    public List<BudgetLineAdjustment> listBudgetLineAdjustments() throws IOException {
        return budgetLineService.listBudgetLineAdjustments();
    }

    @PostMapping("/budget-line-adjustments")
    public BudgetLineAdjustment createBudgetLineAdjustment(@RequestBody CreateBudgetLineAdjustmentRequest request) throws IOException {
        return budgetLineService.createBudgetLineAdjustment(request);
    }

    @PutMapping("/budget-line-adjustments/{adjustmentId}")
    public BudgetLineAdjustment updateBudgetLineAdjustment(@PathVariable String adjustmentId,
                                                           @RequestBody CreateBudgetLineAdjustmentRequest request) throws IOException {
        return budgetLineService.updateBudgetLineAdjustment(adjustmentId, request);
    }

    @DeleteMapping("/budget-line-adjustments/{adjustmentId}")
    public ResponseEntity<Void> deleteBudgetLineAdjustment(@PathVariable String adjustmentId) throws IOException {
        budgetLineService.deleteBudgetLineAdjustment(adjustmentId);
        return ResponseEntity.noContent().build();
    }

    // ── Business Epics ────────────────────────────────────────────────────────

    @GetMapping("/business-epics")
    public List<BusinessEpic> listBusinessEpics() throws IOException {
        return budgetLineService.listBusinessEpics();
    }

    @PostMapping("/business-epics")
    public BusinessEpic createBusinessEpic(@RequestBody CreateBusinessEpicRequest request) throws IOException {
        return budgetLineService.createBusinessEpic(request);
    }

    @PostMapping("/business-epics/import-csv")
    public ImportCsvResponse importBusinessEpicsCsv(@RequestBody ImportBusinessEpicsCsvRequest request) throws IOException {
        return budgetLineService.importBusinessEpicsCsv(request);
    }

    @PutMapping("/business-epics/{epicId}")
    public BusinessEpic updateBusinessEpic(@PathVariable String epicId, @RequestBody CreateBusinessEpicRequest request) throws IOException {
        return budgetLineService.updateBusinessEpic(epicId, request);
    }

    @DeleteMapping("/business-epics/{epicId}")
    public ResponseEntity<Void> deleteBusinessEpic(@PathVariable String epicId) throws IOException {
        budgetLineService.deleteBusinessEpic(epicId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de budget-lines", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
