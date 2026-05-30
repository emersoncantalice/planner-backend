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
public class IncidentController {
    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);
    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    private static String role(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_ROLE);
        return v != null ? v.toString() : "USER";
    }

    @GetMapping("/incidents")
    public List<Incident> listIncidents() throws IOException {
        return incidentService.listIncidents();
    }

    @PostMapping("/incidents")
    public Incident createIncident(@RequestBody CreateIncidentRequest request,
                                    HttpServletRequest req) throws IOException {
        return incidentService.createIncident(request, username(req));
    }

    @PostMapping("/incidents/import-csv")
    public ImportCsvResponse importIncidentsCsv(@RequestBody ImportIncidentsCsvRequest request) throws IOException {
        return incidentService.importIncidentsCsv(request);
    }

    @PutMapping("/incidents/{incidentId}")
    public Incident updateIncident(@PathVariable String incidentId,
                                    @RequestBody CreateIncidentRequest request) throws IOException {
        return incidentService.updateIncident(incidentId, request);
    }

    @DeleteMapping("/incidents/{incidentId}")
    public ResponseEntity<Void> deleteIncident(@PathVariable String incidentId) throws IOException {
        incidentService.deleteIncident(incidentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/incidents/{incidentId}/dono")
    public Incident transferIncidentDono(@PathVariable String incidentId,
                                          @RequestBody TransferOwnershipRequest request,
                                          HttpServletRequest req) throws IOException {
        return incidentService.transferIncidentDono(incidentId, request.novoDono(), username(req), role(req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de incidents", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
