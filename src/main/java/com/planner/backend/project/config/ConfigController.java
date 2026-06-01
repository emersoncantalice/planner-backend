package com.planner.backend.project.config;

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
public class ConfigController {
    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);
    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    // ── Monthly Hours ─────────────────────────────────────────────────────────

    @GetMapping("/monthly-hours")
    public List<MonthlyHours> listMonthlyHours() throws IOException {
        return configService.listMonthlyHours();
    }

    @PutMapping("/monthly-hours/{month}")
    public MonthlyHours upsertMonthlyHours(@PathVariable int month, @RequestBody UpsertMonthlyHoursRequest request) throws IOException {
        return configService.upsertMonthlyHours(month, request);
    }

    @PutMapping("/monthly-hours")
    public List<MonthlyHours> saveAllMonthlyHours(@RequestBody List<UpsertAllMonthlyHoursEntry> items) throws IOException {
        return configService.saveAllMonthlyHours(items);
    }

    // ── Feriados ──────────────────────────────────────────────────────────────

    @GetMapping("/feriados")
    public FeriadosConfig getFeriados() throws IOException {
        return configService.getFeriadosConfig();
    }

    @PutMapping("/feriados")
    public FeriadosConfig saveFeriados(@RequestBody FeriadosConfig config) throws IOException {
        return configService.saveFeriadosConfig(config);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de config", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
