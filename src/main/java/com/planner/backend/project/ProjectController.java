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

    
    @GetMapping("/projects")
    public List<ProjectRecord> listProjects(HttpServletRequest req) throws IOException {
        log.info("GET /api/projects user={} role={}", username(req), role(req));
        return projectService.list(username(req), role(req));
    }

    @PostMapping("/ownership/replace")
    public ReplaceOwnershipResponse replaceOwnership(@RequestBody ReplaceOwnershipRequest request,
                                                     HttpServletRequest req) throws IOException {
        String me = username(req);
        String r = role(req);
        if (!"ADMIN".equals(r)) {
            if (request == null || request.oldValue() == null || !request.oldValue().trim().equalsIgnoreCase(me)) {
                throw new IllegalArgumentException("Sem permissao para trocar dono de outros usuarios.");
            }
        }
        return projectService.replaceOwnershipReferences(request.oldValue(), request.newValue());
    }

    @PostMapping("/projects")
    public ProjectRecord createProject(@RequestBody CreateProjectRequest request,
                                       HttpServletRequest req) throws IOException {
        log.info("POST /api/projects user={} nome={}", username(req), request != null ? request.nome() : null);
        return projectService.create(request, username(req));
    }

    @PatchMapping("/projects/{projectId}/situacao")
    public ProjectRecord updateProjectSituacao(@PathVariable String projectId,
                                               @RequestBody UpdateProjectSituacaoRequest request,
                                               HttpServletRequest req) throws IOException {
        return projectService.updateSituacao(projectId,
                request.situacao(), username(req), role(req));
    }

    @PutMapping("/projects/{projectId}")
    public ProjectRecord updateProject(@PathVariable String projectId, @RequestBody CreateProjectRequest request) throws IOException {
        return projectService.updateProject(projectId, request);
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable String projectId) throws IOException {
        log.info("DELETE /api/projects/{}", projectId);
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/projects/{projectId}/dono")
    public ProjectRecord transferProjectDono(@PathVariable String projectId,
                                              @RequestBody TransferOwnershipRequest request,
                                              HttpServletRequest req) throws IOException {
        return projectService.transferProjectDono(projectId, request.novoDono(), username(req), role(req));
    }

    @GetMapping("/projects/{projectId}")
    public ProjectRecord getProject(@PathVariable String projectId) throws IOException {
        return projectService.getById(projectId);
    }

    @PostMapping("/projects/{projectId}/steps")
    public ProjectRecord addStep(@PathVariable String projectId, @RequestBody CreateStepRequest request) throws IOException {
        return projectService.addStep(projectId, request);
    }

    @PatchMapping("/projects/{projectId}/steps/{stepId}")
    public ProjectRecord toggleStep(@PathVariable String projectId, @PathVariable String stepId, @RequestBody ToggleStepRequest request)
            throws IOException {
        return projectService.toggleStep(projectId, stepId, request);
    }

    @PostMapping("/projects/{projectId}/allocations")
    public ProjectRecord addAllocation(@PathVariable String projectId, @RequestBody CreateAllocationRequest request) throws IOException {
        return projectService.addAllocation(projectId, request);
    }

    @GetMapping("/profiles")
    public List<Profile> listProfiles() throws IOException {
        return projectService.listProfiles();
    }

    @PostMapping("/profiles")
    public Profile createProfile(@RequestBody CreateProfileRequest request) throws IOException {
        return projectService.createProfile(request);
    }

    @PostMapping("/profiles/import-csv")
    public ImportCsvResponse importProfilesCsv(@RequestBody ImportProfilesCsvRequest request) throws IOException {
        return projectService.importProfilesCsv(request);
    }

    @PutMapping("/profiles/{profileId}")
    public Profile updateProfile(@PathVariable String profileId, @RequestBody CreateProfileRequest request) throws IOException {
        return projectService.updateProfile(profileId, request);
    }

    @DeleteMapping("/profiles/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String profileId) throws IOException {
        projectService.deleteProfile(profileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/budget-lines")
    public List<BudgetLine> listBudgetLines(HttpServletRequest req) throws IOException {
        return projectService.listBudgetLines(username(req), role(req));
    }

    @PostMapping("/budget-lines")
    public BudgetLine createBudgetLine(@RequestBody CreateBudgetLineRequest request,
                                       HttpServletRequest req) throws IOException {
        return projectService.createBudgetLine(request, username(req));
    }

    @PatchMapping("/budget-lines/{budgetLineId}/situacao")
    public BudgetLine updateBudgetLineSituacao(@PathVariable String budgetLineId,
                                               @RequestBody UpdateBudgetLineSituacaoRequest request,
                                               HttpServletRequest req) throws IOException {
        return projectService.updateBudgetLineSituacao(budgetLineId,
                request.situacao(), username(req), role(req));
    }

    @PostMapping("/budget-lines/import-csv")
    public ImportCsvResponse importBudgetLinesCsv(@RequestBody ImportBudgetLinesCsvRequest request) throws IOException {
        return projectService.importBudgetLinesCsv(request);
    }

    @PutMapping("/budget-lines/{budgetLineId}")
    public BudgetLine updateBudgetLine(@PathVariable String budgetLineId, @RequestBody CreateBudgetLineRequest request) throws IOException {
        return projectService.updateBudgetLine(budgetLineId, request);
    }

    @DeleteMapping("/budget-lines/{budgetLineId}")
    public ResponseEntity<Void> deleteBudgetLine(@PathVariable String budgetLineId) throws IOException {
        projectService.deleteBudgetLine(budgetLineId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/budget-lines/{budgetLineId}/dono")
    public BudgetLine transferBudgetLineDono(@PathVariable String budgetLineId,
                                              @RequestBody TransferOwnershipRequest request,
                                              HttpServletRequest req) throws IOException {
        return projectService.transferBudgetLineDono(budgetLineId, request.novoDono(), username(req), role(req));
    }

    @GetMapping("/budget-line-adjustments")
    public List<BudgetLineAdjustment> listBudgetLineAdjustments() throws IOException {
        return projectService.listBudgetLineAdjustments();
    }

    @PostMapping("/budget-line-adjustments")
    public BudgetLineAdjustment createBudgetLineAdjustment(@RequestBody CreateBudgetLineAdjustmentRequest request) throws IOException {
        return projectService.createBudgetLineAdjustment(request);
    }

    @PutMapping("/budget-line-adjustments/{adjustmentId}")
    public BudgetLineAdjustment updateBudgetLineAdjustment(@PathVariable String adjustmentId, @RequestBody CreateBudgetLineAdjustmentRequest request) throws IOException {
        return projectService.updateBudgetLineAdjustment(adjustmentId, request);
    }

    @DeleteMapping("/budget-line-adjustments/{adjustmentId}")
    public ResponseEntity<Void> deleteBudgetLineAdjustment(@PathVariable String adjustmentId) throws IOException {
        projectService.deleteBudgetLineAdjustment(adjustmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/business-epics")
    public List<BusinessEpic> listBusinessEpics() throws IOException {
        return projectService.listBusinessEpics();
    }

    @PostMapping("/business-epics")
    public BusinessEpic createBusinessEpic(@RequestBody CreateBusinessEpicRequest request) throws IOException {
        return projectService.createBusinessEpic(request);
    }

    @PostMapping("/business-epics/import-csv")
    public ImportCsvResponse importBusinessEpicsCsv(@RequestBody ImportBusinessEpicsCsvRequest request) throws IOException {
        return projectService.importBusinessEpicsCsv(request);
    }

    @PutMapping("/business-epics/{epicId}")
    public BusinessEpic updateBusinessEpic(@PathVariable String epicId, @RequestBody CreateBusinessEpicRequest request) throws IOException {
        return projectService.updateBusinessEpic(epicId, request);
    }

    @DeleteMapping("/business-epics/{epicId}")
    public ResponseEntity<Void> deleteBusinessEpic(@PathVariable String epicId) throws IOException {
        projectService.deleteBusinessEpic(epicId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/budget-allocations")
    public List<BudgetAllocation> listBudgetAllocations() throws IOException {
        return projectService.listBudgetAllocations();
    }

    @PostMapping("/budget-allocations")
    public BudgetAllocation createBudgetAllocation(@RequestBody CreateBudgetAllocationRequest request) throws IOException {
        return projectService.createBudgetAllocation(request);
    }

    @PutMapping("/budget-allocations/{allocationId}")
    public BudgetAllocation updateBudgetAllocation(@PathVariable String allocationId, @RequestBody CreateBudgetAllocationRequest request) throws IOException {
        return projectService.updateBudgetAllocation(allocationId, request);
    }

    @DeleteMapping("/budget-allocations/{allocationId}")
    public ResponseEntity<Void> deleteBudgetAllocation(@PathVariable String allocationId) throws IOException {
        projectService.deleteBudgetAllocation(allocationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/people")
    public List<Person> listPeople() throws IOException {
        return projectService.listPeople();
    }

    @PostMapping("/people")
    public Person createPerson(@RequestBody CreatePersonRequest request) throws IOException {
        return projectService.createPerson(request);
    }

    @PutMapping("/people/{personId}")
    public Person updatePerson(@PathVariable String personId, @RequestBody CreatePersonRequest request) throws IOException {
        return projectService.updatePerson(personId, request);
    }

    @DeleteMapping("/people/{personId}")
    public ResponseEntity<Void> deletePerson(@PathVariable String personId) throws IOException {
        projectService.deletePerson(personId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/people/import-csv")
    public ImportPeopleCsvResponse importPeopleCsv(@RequestBody ImportPeopleCsvRequest request) throws IOException {
        return projectService.importPeopleCsv(request);
    }

    @GetMapping("/monthly-hours")
    public List<MonthlyHours> listMonthlyHours() throws IOException {
        return projectService.listMonthlyHours();
    }

    @PutMapping("/monthly-hours/{month}")
    public MonthlyHours upsertMonthlyHours(@PathVariable int month, @RequestBody UpsertMonthlyHoursRequest request) throws IOException {
        return projectService.upsertMonthlyHours(month, request);
    }

    @GetMapping("/allocation-payments")
    public List<AllocationPaymentState> listAllocationPayments() throws IOException {
        return projectService.listAllocationPayments();
    }

    @PutMapping("/allocation-payments/{allocationId}/{month}")
    public AllocationPaymentState upsertAllocationPayment(
            @PathVariable String allocationId,
            @PathVariable int month,
            @RequestBody UpdateAllocationPaymentRequest request,
            HttpServletRequest req) throws IOException {
        boolean paid = request != null && Boolean.TRUE.equals(request.paid());
        return projectService.upsertAllocationPayment(allocationId, month, paid, username(req));
    }

    @GetMapping("/lo-presence")
    public List<LoPresenceState> listLoPresence() throws IOException {
        return projectService.listLoPresence();
    }

    @PutMapping("/lo-presence")
    public LoPresenceState upsertLoPresence(
            @RequestBody UpsertLoPresenceRequest request,
            HttpServletRequest req) throws IOException {
        return projectService.upsertLoPresence(request != null ? request.loId() : null, username(req));
    }

    @GetMapping("/allocation-monthly-state")
    public List<AllocationMonthlyState> listAllocationMonthlyStates() throws IOException {
        return projectService.listAllocationMonthlyStates();
    }

    @PutMapping("/allocation-monthly-state/{allocationId}/{month}")
    public AllocationMonthlyState upsertAllocationMonthlyState(
            @PathVariable String allocationId,
            @PathVariable int month,
            @RequestBody UpdateAllocationMonthlyStateRequest request,
            HttpServletRequest req) throws IOException {
        return projectService.upsertAllocationMonthlyState(allocationId, month, request, username(req));
    }

    @GetMapping("/allocation-percent")
    public List<AllocationPercentConfig> listAllocationPercents() throws IOException {
        return projectService.listAllocationPercents();
    }

    @PutMapping("/allocation-percent/{allocationId}")
    public AllocationPercentConfig upsertAllocationPercent(
            @PathVariable String allocationId,
            @RequestBody UpsertAllocationPercentRequest request,
            HttpServletRequest req) throws IOException {
        java.math.BigDecimal pct = request != null ? request.percentual() : null;
        return projectService.upsertAllocationPercent(allocationId, pct, username(req));
    }

    @GetMapping("/lo-realizado")
    public List<LoRealizadoConfig> listLoRealizado() throws IOException {
        return projectService.listLoRealizado();
    }

    @PutMapping("/lo-realizado/{loId}/{month}")
    public LoRealizadoConfig upsertLoRealizado(
            @PathVariable String loId,
            @PathVariable int month,
            @RequestBody UpsertLoRealizadoRequest request,
            HttpServletRequest req) throws IOException {
        java.math.BigDecimal valor = request != null ? request.valor() : null;
        return projectService.upsertLoRealizado(loId, month, valor, username(req));
    }

    @GetMapping("/allocation-cursors")
    public List<AllocationCursorState> listAllocationCursors(@RequestParam(required = false) String loId) throws IOException {
        return projectService.listAllocationCursors(loId);
    }

    @PutMapping("/allocation-cursors")
    public AllocationCursorState upsertAllocationCursor(
            @RequestBody UpsertAllocationCursorRequest request,
            HttpServletRequest req) throws IOException {
        return projectService.upsertAllocationCursor(request, username(req));
    }

    @GetMapping("/consultancies")
    public List<Consultancy> listConsultancies() throws IOException {
        return projectService.listConsultancies();
    }

    @PostMapping("/consultancies")
    public Consultancy createConsultancy(@RequestBody CreateConsultancyRequest request) throws IOException {
        return projectService.createConsultancy(request);
    }

    @PostMapping("/consultancies/import-csv")
    public ImportCsvResponse importConsultanciesCsv(@RequestBody ImportConsultanciesCsvRequest request) throws IOException {
        return projectService.importConsultanciesCsv(request);
    }

    @PutMapping("/consultancies/{consultancyId}")
    public Consultancy updateConsultancy(@PathVariable String consultancyId, @RequestBody CreateConsultancyRequest request) throws IOException {
        return projectService.updateConsultancy(consultancyId, request);
    }

    @DeleteMapping("/consultancies/{consultancyId}")
    public ResponseEntity<Void> deleteConsultancy(@PathVariable String consultancyId) throws IOException {
        projectService.deleteConsultancy(consultancyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/focal-points")
    public List<FocalPoint> listFocalPoints() throws IOException {
        return projectService.listFocalPoints();
    }

    @PostMapping("/focal-points")
    public FocalPoint createFocalPoint(@RequestBody CreateFocalPointRequest request) throws IOException {
        return projectService.createFocalPoint(request);
    }

    @PostMapping("/focal-points/import-csv")
    public ImportCsvResponse importFocalPointsCsv(@RequestBody ImportFocalPointsCsvRequest request) throws IOException {
        return projectService.importFocalPointsCsv(request);
    }

    @PutMapping("/focal-points/{focalPointId}")
    public FocalPoint updateFocalPoint(@PathVariable String focalPointId, @RequestBody CreateFocalPointRequest request) throws IOException {
        return projectService.updateFocalPoint(focalPointId, request);
    }

    @DeleteMapping("/focal-points/{focalPointId}")
    public ResponseEntity<Void> deleteFocalPoint(@PathVariable String focalPointId) throws IOException {
        projectService.deleteFocalPoint(focalPointId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/finance/entries")
    public ProjectRecord addFinanceEntry(@PathVariable String projectId, @RequestBody CreateFinanceEntryRequest request)
            throws IOException {
        return projectService.addFinanceEntry(projectId, request);
    }

    @PostMapping("/projects/{projectId}/schedule/items")
    public ProjectRecord addScheduleItem(@PathVariable String projectId, @RequestBody CreateScheduleItemRequest request) throws IOException {
        return projectService.addScheduleItem(projectId, request);
    }

    @PatchMapping("/projects/{projectId}/schedule/items/{itemId}")
    public ProjectRecord updateScheduleItem(
            @PathVariable String projectId,
            @PathVariable String itemId,
            @RequestBody UpdateScheduleItemRequest request) throws IOException {
        return projectService.updateScheduleItem(projectId, itemId, request);
    }

    @PatchMapping("/projects/{projectId}/schedule/reorder")
    public ProjectRecord reorderSchedule(@PathVariable String projectId, @RequestBody ReorderScheduleRequest request) throws IOException {
        return projectService.reorderSchedule(projectId, request);
    }

    @PostMapping("/projects/{projectId}/replanejamentos")
    public ProjectRecord addReplanningEvent(@PathVariable String projectId, @RequestBody AddReplanningEventRequest request) throws IOException {
        return projectService.addReplanningEvent(projectId, request);
    }

    @PostMapping("/projects/{projectId}/risks")
    public ProjectRecord addRiskItem(@PathVariable String projectId, @RequestBody CreateRiskItemRequest request) throws IOException {
        return projectService.addRiskItem(projectId, request);
    }

    @PatchMapping("/projects/{projectId}/risks/{riskId}")
    public ProjectRecord updateRiskItemStatus(
            @PathVariable String projectId,
            @PathVariable String riskId,
            @RequestBody UpdateRiskItemStatusRequest request) throws IOException {
        return projectService.updateRiskItemStatus(projectId, riskId, request);
    }

    @GetMapping("/risks")
    public List<GlobalRisk> listRisks() throws IOException {
        return projectService.listGlobalRisks();
    }

    @PostMapping("/risks")
    public GlobalRisk createRisk(@RequestBody CreateGlobalRiskRequest request,
                                  HttpServletRequest req) throws IOException {
        return projectService.createGlobalRisk(request, username(req));
    }

    @PostMapping("/risks/import-csv")
    public ImportCsvResponse importRisksCsv(@RequestBody ImportRisksCsvRequest request) throws IOException {
        return projectService.importGlobalRisksCsv(request);
    }

    @PatchMapping("/risks/{riskId}")
    public GlobalRisk updateRiskStatus(@PathVariable String riskId, @RequestBody UpdateGlobalRiskStatusRequest request) throws IOException {
        return projectService.updateGlobalRiskStatus(riskId, request);
    }

    @PutMapping("/risks/{riskId}")
    public GlobalRisk updateRisk(@PathVariable String riskId, @RequestBody CreateGlobalRiskRequest request) throws IOException {
        return projectService.updateGlobalRisk(riskId, request);
    }

    @PostMapping("/risks/{riskId}/postpone")
    public GlobalRisk postponeRisk(@PathVariable String riskId, @RequestBody PostponeGlobalRiskRequest request) throws IOException {
        return projectService.postponeGlobalRisk(riskId, request);
    }

    @DeleteMapping("/risks/{riskId}")
    public ResponseEntity<Void> deleteRisk(@PathVariable String riskId) throws IOException {
        projectService.deleteGlobalRisk(riskId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/risks/{riskId}/dono")
    public GlobalRisk transferRiskDono(@PathVariable String riskId,
                                        @RequestBody TransferOwnershipRequest request,
                                        HttpServletRequest req) throws IOException {
        return projectService.transferGlobalRiskDono(riskId, request.novoDono(), username(req), role(req));
    }


    @GetMapping("/activities")
    public List<ProjectActivity> listAllActivities(HttpServletRequest req) throws IOException {
        return projectService.listAllActivities();
    }

    
    @GetMapping("/absences")
    public List<Absence> listAbsences() throws IOException {
        return projectService.listAbsences();
    }

    @PostMapping("/absences")
    public Absence createAbsence(@RequestBody CreateAbsenceRequest request) throws IOException {
        return projectService.createAbsence(request);
    }

    @PutMapping("/absences/{absenceId}")
    public Absence updateAbsence(@PathVariable String absenceId, @RequestBody CreateAbsenceRequest request) throws IOException {
        return projectService.updateAbsence(absenceId, request);
    }

    @DeleteMapping("/absences/{absenceId}")
    public ResponseEntity<Void> deleteAbsence(@PathVariable String absenceId) throws IOException {
        projectService.deleteAbsence(absenceId);
        return ResponseEntity.noContent().build();
    }

    
    @GetMapping("/incidents")
    public List<Incident> listIncidents() throws IOException {
        return projectService.listIncidents();
    }

    @PostMapping("/incidents")
    public Incident createIncident(@RequestBody CreateIncidentRequest request,
                                    HttpServletRequest req) throws IOException {
        return projectService.createIncident(request, username(req));
    }

    @PostMapping("/incidents/import-csv")
    public ImportCsvResponse importIncidentsCsv(@RequestBody ImportIncidentsCsvRequest request) throws IOException {
        return projectService.importIncidentsCsv(request);
    }

    @PutMapping("/incidents/{incidentId}")
    public Incident updateIncident(@PathVariable String incidentId, @RequestBody CreateIncidentRequest request) throws IOException {
        return projectService.updateIncident(incidentId, request);
    }

    @DeleteMapping("/incidents/{incidentId}")
    public ResponseEntity<Void> deleteIncident(@PathVariable String incidentId) throws IOException {
        projectService.deleteIncident(incidentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/incidents/{incidentId}/dono")
    public Incident transferIncidentDono(@PathVariable String incidentId,
                                          @RequestBody TransferOwnershipRequest request,
                                          HttpServletRequest req) throws IOException {
        return projectService.transferIncidentDono(incidentId, request.novoDono(), username(req), role(req));
    }


    @GetMapping("/technical-debts")
    public List<TechnicalDebt> listTechnicalDebts() throws IOException {
        return projectService.listTechnicalDebts();
    }

    @PostMapping("/technical-debts")
    public TechnicalDebt createTechnicalDebt(@RequestBody CreateTechnicalDebtRequest request,
                                              HttpServletRequest req) throws IOException {
        return projectService.createTechnicalDebt(request, username(req));
    }

    @PutMapping("/technical-debts/{debtId}")
    public TechnicalDebt updateTechnicalDebt(@PathVariable String debtId, @RequestBody CreateTechnicalDebtRequest request) throws IOException {
        return projectService.updateTechnicalDebt(debtId, request);
    }

    @DeleteMapping("/technical-debts/{debtId}")
    public ResponseEntity<Void> deleteTechnicalDebt(@PathVariable String debtId) throws IOException {
        projectService.deleteTechnicalDebt(debtId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/technical-debts/{debtId}/dono")
    public TechnicalDebt transferTechnicalDebtDono(@PathVariable String debtId,
                                                    @RequestBody TransferOwnershipRequest request,
                                                    HttpServletRequest req) throws IOException {
        return projectService.transferTechnicalDebtDono(debtId, request.novoDono(), username(req), role(req));
    }


    @GetMapping("/indicators")
    public List<Indicator> listIndicators() throws IOException {
        return projectService.listIndicators();
    }

    @PostMapping("/indicators")
    public Indicator createIndicator(@RequestBody CreateIndicatorRequest request,
                                      HttpServletRequest req) throws IOException {
        return projectService.createIndicator(request, username(req));
    }

    @PutMapping("/indicators/{indicatorId}")
    public Indicator updateIndicator(@PathVariable String indicatorId, @RequestBody CreateIndicatorRequest request) throws IOException {
        return projectService.updateIndicator(indicatorId, request);
    }

    @DeleteMapping("/indicators/{indicatorId}")
    public ResponseEntity<Void> deleteIndicator(@PathVariable String indicatorId) throws IOException {
        projectService.deleteIndicator(indicatorId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/indicators/{indicatorId}/dono")
    public Indicator transferIndicatorDono(@PathVariable String indicatorId,
                                            @RequestBody TransferOwnershipRequest request,
                                            HttpServletRequest req) throws IOException {
        return projectService.transferIndicatorDono(indicatorId, request.novoDono(), username(req), role(req));
    }

    @PostMapping("/indicators/{indicatorId}/cycles")
    public Indicator addIndicatorCycle(@PathVariable String indicatorId, @RequestBody CreateIndicatorCycleRequest request) throws IOException {
        return projectService.addIndicatorCycle(indicatorId, request);
    }

    @PutMapping("/indicators/{indicatorId}/cycles/{cycleId}")
    public Indicator updateIndicatorCycle(@PathVariable String indicatorId, @PathVariable String cycleId, @RequestBody UpdateIndicatorCycleRequest request) throws IOException {
        return projectService.updateIndicatorCycle(indicatorId, cycleId, request);
    }

    @DeleteMapping("/indicators/{indicatorId}/cycles/{cycleId}")
    public Indicator deleteIndicatorCycle(@PathVariable String indicatorId, @PathVariable String cycleId) throws IOException {
        return projectService.deleteIndicatorCycle(indicatorId, cycleId);
    }

    @PostMapping("/indicators/{indicatorId}/actions")
    public Indicator addIndicatorAction(@PathVariable String indicatorId, @RequestBody CreateIndicatorActionRequest request) throws IOException {
        return projectService.addIndicatorAction(indicatorId, request);
    }

    @PutMapping("/indicators/{indicatorId}/actions/{actionId}")
    public Indicator updateIndicatorAction(@PathVariable String indicatorId, @PathVariable String actionId, @RequestBody UpdateIndicatorActionRequest request) throws IOException {
        return projectService.updateIndicatorAction(indicatorId, actionId, request);
    }

    @DeleteMapping("/indicators/{indicatorId}/actions/{actionId}")
    public Indicator deleteIndicatorAction(@PathVariable String indicatorId, @PathVariable String actionId) throws IOException {
        return projectService.deleteIndicatorAction(indicatorId, actionId);
    }

    @GetMapping("/reports/overview")
    public List<ProjectOverviewResponse> overview(HttpServletRequest req) throws IOException {
        log.info("GET /api/reports/overview user={} role={}", username(req), role(req));
        return projectService.overview(username(req), role(req));
    }

    @GetMapping("/reports/project/{projectId}")
    public ProjectRecord projectReport(@PathVariable String projectId) throws IOException {
        return projectService.getById(projectId);
    }

    
    @GetMapping("/feriados")
    public FeriadosConfig getFeriados() throws IOException {
        return projectService.getFeriadosConfig();
    }

    @PutMapping("/feriados")
    public FeriadosConfig saveFeriados(@RequestBody FeriadosConfig config) throws IOException {
        return projectService.saveFeriadosConfig(config);
    }

    
    @GetMapping("/gantt-configs/{projectId}")
    public GanttProjectConfig getGanttConfig(@PathVariable String projectId) throws IOException {
        return projectService.getGanttConfig(projectId);
    }

    @PutMapping("/gantt-configs/{projectId}")
    public GanttProjectConfig saveGanttConfig(
            @PathVariable String projectId,
            @RequestBody SaveGanttConfigRequest request) throws IOException {
        return projectService.saveGanttConfig(projectId, request);
    }

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
