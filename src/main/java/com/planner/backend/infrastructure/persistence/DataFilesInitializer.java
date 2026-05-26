package com.planner.backend.infrastructure.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataFilesInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataFilesInitializer.class);

    private final String dataDir;

    public DataFilesInitializer(@Value("${planner.data-dir:data}") String dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> requiredFiles = List.of(
                "users.json",
                "sessions.json",
                "projects.json",
                "profiles.json",
                "budget-lines.json",
                "business-epics.json",
                "budget-allocations.json",
                "budget-line-adjustments.json",
                "risks.json",
                "people.json",
                "monthly-hours.json",
                "consultancies.json",
                "focal-points.json",
                "absences.json",
                "incidents.json",
                "technical-debts.json",
                "indicators.json",
                "gantt-configs.json");

        for (String fileName : requiredFiles) {
            ensureFile(Path.of(dataDir, fileName), "[]");
        }

        // Feriados config is a single JSON object, not a list
        ensureFile(Path.of(dataDir, "feriados.json"), "{}");
    }

    private void ensureFile(Path path, String defaultContent) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, defaultContent, StandardCharsets.UTF_8);
        log.info("Arquivo de dados criado automaticamente: {}", path);
    }
}
