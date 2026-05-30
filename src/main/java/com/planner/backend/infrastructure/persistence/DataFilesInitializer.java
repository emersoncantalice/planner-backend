package com.planner.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planner.backend.auth.AuthModels.UserRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataFilesInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataFilesInitializer.class);

    private final String dataDir;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataFilesInitializer(
            @Value("${planner.data-dir:data}") String dataDir,
            ObjectMapper objectMapper) {
        this.dataDir = dataDir;
        this.objectMapper = objectMapper;
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
                "gantt-configs.json",
                "allocation-percent.json",
                "lo-realizado.json",
                "project-budgets.json");

        for (String fileName : requiredFiles) {
            ensureFile(Path.of(dataDir, fileName), "[]");
        }

        ensureFile(Path.of(dataDir, "feriados.json"), "{}");
        ensureDefaultAdminUser(Path.of(dataDir, "users.json"));
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

    private void ensureDefaultAdminUser(Path usersPath) throws IOException {
        List<UserRecord> users = readUsers(usersPath);
        if (!users.isEmpty()) return;

        users.add(new UserRecord(
                "admin",
                "Administrador",
                passwordEncoder.encode("admin"),
                OffsetDateTime.now(),
                "ADMIN",
                "APPROVED",
                null
        ));

        objectMapper.writeValue(usersPath.toFile(), users);
        log.info("Usuario padrao criado em {} (username=admin).", usersPath);
    }

    private List<UserRecord> readUsers(Path usersPath) throws IOException {
        if (!Files.exists(usersPath)) return new ArrayList<>();
        if (Files.size(usersPath) == 0L) return new ArrayList<>();
        try {
            List<UserRecord> users = objectMapper.readValue(usersPath.toFile(), new TypeReference<List<UserRecord>>() {});
            return users == null ? new ArrayList<>() : users;
        } catch (Exception ex) {
            log.warn("Nao foi possivel ler users.json. Mantendo sem usuario padrao automatico. Motivo: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }
}
