package com.planner.backend.project;

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
public class ProjectController {
    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    private static String role(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_ROLE);
        return v != null ? v.toString() : "USER";
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    @GetMapping("/projects")
    public List<ProjectRecord> listProjects(HttpServletRequest req) throws IOException {
        log.info("GET /api/projects user={} role={}", username(req), role(req));
        return projectService.list(username(req), role(req));
    }

    @PostMapping("/projects")
    public ProjectRecord createProject(@RequestBody CreateProjectRequest request,
                                       HttpServletRequest req) throws IOException {
        log.info("POST /api/projects user={} nome={}", username(req), request != null ? request.nome() : null);
        return projectService.create(request, username(req));
    }

    @GetMapping("/projects/{projectId}")
    public ProjectRecord getProject(@PathVariable String projectId) throws IOException {
        return projectService.getById(projectId);
    }

    @PostMapping("/projects/{projectId}/duplicate")
    public ProjectRecord duplicateProject(@PathVariable String projectId,
                                          HttpServletRequest req) throws IOException {
        log.info("POST /api/projects/{}/duplicate user={}", projectId, username(req));
        return projectService.duplicate(projectId, username(req));
    }

    @PutMapping("/projects/{projectId}")
    public ProjectRecord updateProject(@PathVariable String projectId,
                                       @RequestBody CreateProjectRequest request) throws IOException {
        return projectService.updateProject(projectId, request);
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable String projectId) throws IOException {
        log.info("DELETE /api/projects/{}", projectId);
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/projects/{projectId}/situacao")
    public ProjectRecord updateProjectSituacao(@PathVariable String projectId,
                                               @RequestBody UpdateProjectSituacaoRequest request,
                                               HttpServletRequest req) throws IOException {
        return projectService.updateSituacao(projectId, request.situacao(), username(req), role(req));
    }

    @PatchMapping("/projects/{projectId}/dono")
    public ProjectRecord transferProjectDono(@PathVariable String projectId,
                                              @RequestBody TransferOwnershipRequest request,
                                              HttpServletRequest req) throws IOException {
        return projectService.transferProjectDono(projectId, request.novoDono(), username(req), role(req));
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    @PostMapping("/projects/{projectId}/steps")
    public ProjectRecord addStep(@PathVariable String projectId,
                                  @RequestBody CreateStepRequest request) throws IOException {
        return projectService.addStep(projectId, request);
    }

    @PatchMapping("/projects/{projectId}/steps/{stepId}")
    public ProjectRecord toggleStep(@PathVariable String projectId, @PathVariable String stepId,
                                     @RequestBody ToggleStepRequest request) throws IOException {
        return projectService.toggleStep(projectId, stepId, request);
    }

    // ── Project Allocations ───────────────────────────────────────────────────

    @PostMapping("/projects/{projectId}/allocations")
    public ProjectRecord addAllocation(@PathVariable String projectId,
                                        @RequestBody CreateAllocationRequest request) throws IOException {
        return projectService.addAllocation(projectId, request);
    }

    // ── Finance ───────────────────────────────────────────────────────────────

    @PostMapping("/projects/{projectId}/finance/entries")
    public ProjectRecord addFinanceEntry(@PathVariable String projectId,
                                          @RequestBody CreateFinanceEntryRequest request) throws IOException {
        return projectService.addFinanceEntry(projectId, request);
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    @PostMapping("/projects/{projectId}/schedule/items")
    public ProjectRecord addScheduleItem(@PathVariable String projectId,
                                          @RequestBody CreateScheduleItemRequest request) throws IOException {
        return projectService.addScheduleItem(projectId, request);
    }

    @PatchMapping("/projects/{projectId}/schedule/items/{itemId}")
    public ProjectRecord updateScheduleItem(@PathVariable String projectId,
                                             @PathVariable String itemId,
                                             @RequestBody UpdateScheduleItemRequest request) throws IOException {
        return projectService.updateScheduleItem(projectId, itemId, request);
    }

    @PatchMapping("/projects/{projectId}/schedule/reorder")
    public ProjectRecord reorderSchedule(@PathVariable String projectId,
                                          @RequestBody ReorderScheduleRequest request) throws IOException {
        return projectService.reorderSchedule(projectId, request);
    }

    // ── Replanning ────────────────────────────────────────────────────────────

    @PostMapping("/projects/{projectId}/replanejamentos")
    public ProjectRecord addReplanningEvent(@PathVariable String projectId,
                                             @RequestBody AddReplanningEventRequest request) throws IOException {
        return projectService.addReplanningEvent(projectId, request);
    }

    // ── Project-level Risks ───────────────────────────────────────────────────

    @PostMapping("/projects/{projectId}/risks")
    public ProjectRecord addRiskItem(@PathVariable String projectId,
                                      @RequestBody CreateRiskItemRequest request) throws IOException {
        return projectService.addRiskItem(projectId, request);
    }

    @PatchMapping("/projects/{projectId}/risks/{riskId}")
    public ProjectRecord updateRiskItemStatus(@PathVariable String projectId,
                                               @PathVariable String riskId,
                                               @RequestBody UpdateRiskItemStatusRequest request) throws IOException {
        return projectService.updateRiskItemStatus(projectId, riskId, request);
    }

    // ── Activities ────────────────────────────────────────────────────────────

    @GetMapping("/activities")
    public List<ProjectActivity> listAllActivities() throws IOException {
        return projectService.listAllActivities();
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    @GetMapping("/reports/overview")
    public List<ProjectOverviewResponse> overview(HttpServletRequest req) throws IOException {
        log.info("GET /api/reports/overview user={} role={}", username(req), role(req));
        return projectService.overview(username(req), role(req));
    }

    @GetMapping("/reports/project/{projectId}")
    public ProjectRecord projectReport(@PathVariable String projectId) throws IOException {
        return projectService.getById(projectId);
    }

    // ── Gantt Configs ─────────────────────────────────────────────────────────

    @GetMapping("/gantt-configs/{projectId}")
    public GanttProjectConfig getGanttConfig(@PathVariable String projectId) throws IOException {
        return projectService.getGanttConfig(projectId);
    }

    @PutMapping("/gantt-configs/{projectId}")
    public GanttProjectConfig saveGanttConfig(@PathVariable String projectId,
                                               @RequestBody SaveGanttConfigRequest request) throws IOException {
        return projectService.saveGanttConfig(projectId, request);
    }

    // ── Project Budgets ───────────────────────────────────────────────────────

    @GetMapping("/project-budgets")
    public List<ProjectBudget> listProjectBudgets() throws IOException {
        return projectService.listProjectBudgets();
    }

    @PostMapping("/project-budgets")
    public ProjectBudget createProjectBudget(@RequestBody CreateProjectBudgetRequest request,
                                              HttpServletRequest req) throws IOException {
        return projectService.createProjectBudget(request, username(req));
    }

    @PutMapping("/project-budgets/{id}")
    public ProjectBudget updateProjectBudget(@PathVariable String id,
                                              @RequestBody CreateProjectBudgetRequest request) throws IOException {
        return projectService.updateProjectBudget(id, request);
    }

    @DeleteMapping("/project-budgets/{id}")
    public ResponseEntity<Void> deleteProjectBudget(@PathVariable String id) throws IOException {
        projectService.deleteProjectBudget(id);
        return ResponseEntity.noContent().build();
    }

    // ── Ownership ─────────────────────────────────────────────────────────────

    @PostMapping("/ownership/replace")
    public ReplaceOwnershipResponse replaceOwnership(@RequestBody ReplaceOwnershipRequest request,
                                                      HttpServletRequest req) throws IOException {
        String me = username(req);
        String r = role(req);
        if (!"ADMIN".equals(r)) {
            if (request == null || request.oldValue() == null
                    || !request.oldValue().trim().equalsIgnoreCase(me)) {
                throw new IllegalArgumentException("Sem permissao para trocar dono de outros usuarios.");
            }
        }
        return projectService.replaceOwnershipReferences(request.oldValue(), request.newValue());
    }

    // ── Exception handlers ────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de projetos", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
