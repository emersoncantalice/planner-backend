package com.planner.backend.project.allocation;

import static com.planner.backend.project.ProjectModels.*;

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
public class BudgetAllocationController {
    private static final Logger log = LoggerFactory.getLogger(BudgetAllocationController.class);
    private final BudgetAllocationService budgetAllocationService;

    public BudgetAllocationController(BudgetAllocationService budgetAllocationService) {
        this.budgetAllocationService = budgetAllocationService;
    }

    @GetMapping("/budget-allocations")
    public List<BudgetAllocation> listBudgetAllocations() throws IOException {
        return budgetAllocationService.listBudgetAllocations();
    }

    @PostMapping("/budget-allocations")
    public BudgetAllocation createBudgetAllocation(@RequestBody CreateBudgetAllocationRequest request) throws IOException {
        return budgetAllocationService.createBudgetAllocation(request);
    }

    @PutMapping("/budget-allocations/{allocationId}")
    public BudgetAllocation updateBudgetAllocation(@PathVariable String allocationId,
                                                    @RequestBody CreateBudgetAllocationRequest request) throws IOException {
        return budgetAllocationService.updateBudgetAllocation(allocationId, request);
    }

    @DeleteMapping("/budget-allocations/{allocationId}")
    public ResponseEntity<Void> deleteBudgetAllocation(@PathVariable String allocationId) throws IOException {
        budgetAllocationService.deleteBudgetAllocation(allocationId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de budget-allocations", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
