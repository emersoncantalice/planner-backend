package com.planner.backend.project.periods;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PeriodsService {

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path periodsPath;
    private final java.nio.file.Path checksPath;

    public PeriodsService(FileJsonStore jsonStore, @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore   = jsonStore;
        this.periodsPath = java.nio.file.Path.of(dataDir, "periods.json");
        this.checksPath  = java.nio.file.Path.of(dataDir, "period-checks.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(periodsPath);
            ensureFile(checksPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de períodos.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path p) throws IOException {
        if (Files.exists(p)) return;
        java.nio.file.Path parent = p.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(p, "[]", StandardCharsets.UTF_8);
    }

    // ── Periods CRUD ──────────────────────────────────────────────────────────

    public List<PeriodRecord> listPeriods() throws IOException {
        return new ArrayList<>(loadPeriods());
    }

    public PeriodRecord createPeriod(CreatePeriodRequest req, String username) throws IOException {
        validate(req);
        PeriodRecord rec = new PeriodRecord(
                UUID.randomUUID().toString(),
                req.titulo().trim(),
                req.descricao() != null ? req.descricao().trim() : "",
                req.tipo().toUpperCase(),
                req.diaInicio(), req.diaFim(),
                req.mesInicio(), req.mesFim(),
                req.cor() != null ? req.cor() : "#3b82f6",
                req.icone() != null ? req.icone() : "📅",
                username, OffsetDateTime.now());
        List<PeriodRecord> all = new ArrayList<>(loadPeriods());
        all.add(rec);
        savePeriods(all);
        return rec;
    }

    public PeriodRecord updatePeriod(String id, CreatePeriodRequest req, String username) throws IOException {
        validate(req);
        List<PeriodRecord> all = new ArrayList<>(loadPeriods());
        for (int i = 0; i < all.size(); i++) {
            if (id.equals(all.get(i).id())) {
                PeriodRecord orig = all.get(i);
                PeriodRecord updated = new PeriodRecord(
                        id, req.titulo().trim(),
                        req.descricao() != null ? req.descricao().trim() : "",
                        req.tipo().toUpperCase(),
                        req.diaInicio(), req.diaFim(),
                        req.mesInicio(), req.mesFim(),
                        req.cor() != null ? req.cor() : "#3b82f6",
                        req.icone() != null ? req.icone() : "📅",
                        orig.criadoPor(), orig.criadoEm());
                all.set(i, updated);
                savePeriods(all);
                clearChecksForPeriod(id);
                return updated;
            }
        }
        throw new IllegalArgumentException("Período não encontrado.");
    }

    public void deletePeriod(String id) throws IOException {
        List<PeriodRecord> all = new ArrayList<>(loadPeriods());
        boolean removed = all.removeIf(p -> id.equals(p.id()));
        if (!removed) throw new IllegalArgumentException("Período não encontrado.");
        savePeriods(all);
        clearChecksForPeriod(id);
    }

    private void validate(CreatePeriodRequest req) {
        if (req == null || req.titulo() == null || req.titulo().isBlank())
            throw new IllegalArgumentException("Título é obrigatório.");
        String tipo = req.tipo() != null ? req.tipo().toUpperCase() : "";
        Set<String> allowed = Set.of("DIARIO", "SEMANAL", "MENSAL", "TRIMESTRAL", "SEMESTRAL", "ANUAL");
        if (!allowed.contains(tipo))
            throw new IllegalArgumentException("Tipo deve ser DIARIO, SEMANAL, MENSAL, TRIMESTRAL, SEMESTRAL ou ANUAL.");
        if (req.diaInicio() < 1 || req.diaInicio() > 31 || req.diaFim() < 1 || req.diaFim() > 31)
            throw new IllegalArgumentException("Dias devem ser entre 1 e 31.");
        if (tipo.equals("SEMANAL") && (req.diaInicio() > 7 || req.diaFim() > 7))
            throw new IllegalArgumentException("Dias devem ser entre 1 e 7 para tipo SEMANAL.");
        if (tipo.equals("ANUAL") || tipo.equals("TRIMESTRAL") || tipo.equals("SEMESTRAL")) {
            if (req.mesInicio() == null || req.mesFim() == null)
                throw new IllegalArgumentException("Meses de início e fim são obrigatórios para tipo ANUAL.");
            if (req.mesInicio() < 1 || req.mesInicio() > 12 || req.mesFim() < 1 || req.mesFim() > 12)
                throw new IllegalArgumentException("Meses devem ser entre 1 e 12.");
        }
    }

    // ── Checks ────────────────────────────────────────────────────────────────

    public List<PeriodCheckRecord> listChecks() throws IOException {
        return new ArrayList<>(loadChecks());
    }

    public PeriodCheckRecord upsertCheck(String periodId, String username, int ano, int mes) throws IOException {
        List<PeriodCheckRecord> all = new ArrayList<>(loadChecks());
        all.removeIf(c -> periodId.equals(c.periodId()) && username.equalsIgnoreCase(c.username())
                && ano == c.ano() && mes == c.mes());
        PeriodCheckRecord rec = new PeriodCheckRecord(periodId, username, ano, mes, OffsetDateTime.now());
        all.add(rec);
        saveChecks(all);
        return rec;
    }

    public void removeCheck(String periodId, String username, int ano, int mes) throws IOException {
        List<PeriodCheckRecord> all = new ArrayList<>(loadChecks());
        all.removeIf(c -> periodId.equals(c.periodId()) && username.equalsIgnoreCase(c.username())
                && ano == c.ano() && mes == c.mes());
        saveChecks(all);
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    private List<PeriodRecord> loadPeriods() throws IOException {
        return jsonStore.readList(periodsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void clearChecksForPeriod(String periodId) throws IOException {
        List<PeriodCheckRecord> checks = new ArrayList<>(loadChecks());
        checks.removeIf(c -> periodId.equals(c.periodId()));
        saveChecks(checks);
    }

    private void savePeriods(List<PeriodRecord> list) throws IOException {
        jsonStore.writeList(periodsPath, list);
    }

    private List<PeriodCheckRecord> loadChecks() throws IOException {
        return jsonStore.readList(checksPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveChecks(List<PeriodCheckRecord> list) throws IOException {
        jsonStore.writeList(checksPath, list);
    }
}
