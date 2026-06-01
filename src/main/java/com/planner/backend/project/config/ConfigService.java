package com.planner.backend.project.config;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {

    private static final java.util.List<Boolean> DEFAULT_DIAS_UTEIS =
            java.util.List.of(false, true, true, true, true, true, false);

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path monthlyHoursPath;
    private final java.nio.file.Path feriadosPath;

    public ConfigService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.monthlyHoursPath = java.nio.file.Path.of(dataDir, "monthly-hours.json");
        this.feriadosPath     = java.nio.file.Path.of(dataDir, "feriados.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(monthlyHoursPath);
            ensureFileObject(feriadosPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de config.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    private void ensureFileObject(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "{}", StandardCharsets.UTF_8);
    }

    // ── Monthly Hours ─────────────────────────────────────────────────────────

    public List<MonthlyHours> listMonthlyHours() throws IOException {
        List<MonthlyHours> hours = new ArrayList<>(loadMonthlyHours());
        hours.sort(Comparator.comparingInt(MonthlyHours::mes));
        return hours;
    }

    public MonthlyHours upsertMonthlyHours(int month, UpsertMonthlyHoursRequest request) throws IOException {
        if (month < 1 || month > 12) throw new IllegalArgumentException("Mes invalido. Use 1..12.");
        if (request == null || request.horas() == null || request.horas().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Horas do mes deve ser maior que zero.");
        List<MonthlyHours> all = new ArrayList<>(loadMonthlyHours());
        for (int i = 0; i < all.size(); i++) {
            MonthlyHours h = all.get(i);
            if (h.mes() == month) {
                MonthlyHours updated = new MonthlyHours(month, request.horas(), OffsetDateTime.now());
                all.set(i, updated);
                saveMonthlyHours(all);
                return updated;
            }
        }
        MonthlyHours created = new MonthlyHours(month, request.horas(), OffsetDateTime.now());
        all.add(created);
        saveMonthlyHours(all);
        return created;
    }

    public List<MonthlyHours> saveAllMonthlyHours(List<UpsertAllMonthlyHoursEntry> items) throws IOException {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Lista de horas nao pode ser vazia.");
        List<MonthlyHours> toSave = new ArrayList<>();
        for (UpsertAllMonthlyHoursEntry item : items) {
            if (item.mes() < 1 || item.mes() > 12) throw new IllegalArgumentException("Mes invalido: " + item.mes());
            if (item.horas() == null || item.horas().compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("Horas invalidas para mes " + item.mes());
            toSave.add(new MonthlyHours(item.mes(), item.horas(), OffsetDateTime.now()));
        }
        toSave.sort(Comparator.comparingInt(MonthlyHours::mes));
        saveMonthlyHours(toSave);
        return toSave;
    }

    // ── Feriados ──────────────────────────────────────────────────────────────

    public FeriadosConfig getFeriadosConfig() throws IOException {
        FeriadosConfig cfg = jsonStore.readObject(feriadosPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        return new FeriadosConfig(
                cfg == null || cfg.feriados() == null ? new ArrayList<>() : cfg.feriados(),
                cfg == null || cfg.federalOverrides() == null ? new java.util.HashMap<>() : cfg.federalOverrides(),
                cfg == null || cfg.diasUteis() == null ? DEFAULT_DIAS_UTEIS : cfg.diasUteis());
    }

    public FeriadosConfig saveFeriadosConfig(FeriadosConfig config) throws IOException {
        FeriadosConfig toSave = new FeriadosConfig(
                config == null || config.feriados() == null ? new ArrayList<>() : config.feriados(),
                config == null || config.federalOverrides() == null ? new java.util.HashMap<>() : config.federalOverrides(),
                config == null || config.diasUteis() == null ? DEFAULT_DIAS_UTEIS : config.diasUteis());
        jsonStore.writeObject(feriadosPath, toSave);
        return toSave;
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<MonthlyHours> loadMonthlyHours() throws IOException {
        return jsonStore.readList(monthlyHoursPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveMonthlyHours(List<MonthlyHours> hours) throws IOException {
        jsonStore.writeList(monthlyHoursPath, hours);
    }
}
