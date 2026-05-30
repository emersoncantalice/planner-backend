package com.planner.backend.project.consulting;

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
public class ConsultingController {
    private static final Logger log = LoggerFactory.getLogger(ConsultingController.class);
    private final ConsultingService consultingService;

    public ConsultingController(ConsultingService consultingService) {
        this.consultingService = consultingService;
    }

    // ── Consultancies ─────────────────────────────────────────────────────────

    @GetMapping("/consultancies")
    public List<Consultancy> listConsultancies() throws IOException {
        return consultingService.listConsultancies();
    }

    @PostMapping("/consultancies")
    public Consultancy createConsultancy(@RequestBody CreateConsultancyRequest request) throws IOException {
        return consultingService.createConsultancy(request);
    }

    @PostMapping("/consultancies/import-csv")
    public ImportCsvResponse importConsultanciesCsv(@RequestBody ImportConsultanciesCsvRequest request) throws IOException {
        return consultingService.importConsultanciesCsv(request);
    }

    @PutMapping("/consultancies/{consultancyId}")
    public Consultancy updateConsultancy(@PathVariable String consultancyId, @RequestBody CreateConsultancyRequest request) throws IOException {
        return consultingService.updateConsultancy(consultancyId, request);
    }

    @DeleteMapping("/consultancies/{consultancyId}")
    public ResponseEntity<Void> deleteConsultancy(@PathVariable String consultancyId) throws IOException {
        consultingService.deleteConsultancy(consultancyId);
        return ResponseEntity.noContent().build();
    }

    // ── Focal Points ──────────────────────────────────────────────────────────

    @GetMapping("/focal-points")
    public List<FocalPoint> listFocalPoints() throws IOException {
        return consultingService.listFocalPoints();
    }

    @PostMapping("/focal-points")
    public FocalPoint createFocalPoint(@RequestBody CreateFocalPointRequest request) throws IOException {
        return consultingService.createFocalPoint(request);
    }

    @PostMapping("/focal-points/import-csv")
    public ImportCsvResponse importFocalPointsCsv(@RequestBody ImportFocalPointsCsvRequest request) throws IOException {
        return consultingService.importFocalPointsCsv(request);
    }

    @PutMapping("/focal-points/{focalPointId}")
    public FocalPoint updateFocalPoint(@PathVariable String focalPointId, @RequestBody CreateFocalPointRequest request) throws IOException {
        return consultingService.updateFocalPoint(focalPointId, request);
    }

    @DeleteMapping("/focal-points/{focalPointId}")
    public ResponseEntity<Void> deleteFocalPoint(@PathVariable String focalPointId) throws IOException {
        consultingService.deleteFocalPoint(focalPointId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de consultancies", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
