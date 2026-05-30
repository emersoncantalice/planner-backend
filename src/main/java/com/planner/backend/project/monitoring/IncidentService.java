package com.planner.backend.project.monitoring;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {
    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path incidentsPath;

    public IncidentService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.incidentsPath = java.nio.file.Path.of(dataDir, "incidents.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(incidentsPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de incidents.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    public List<Incident> listIncidents() throws IOException {
        return loadIncidents().stream().map(this::normalizeIncident).toList();
    }

    public Incident createIncident(CreateIncidentRequest request, String criadoPor) throws IOException {
        validateIncidentRequest(request);
        String initialStatus = normalizeEnum(request.status(), "ABERTO");
        String dono = (criadoPor == null || criadoPor.isBlank()) ? null : criadoPor.trim();
        List<IncidentHistoryEvent> historico = new ArrayList<>();
        historico.add(new IncidentHistoryEvent(UUID.randomUUID().toString(),
                "CRIACAO", "Incidente criado.", null, initialStatus, OffsetDateTime.now()));
        Incident created = new Incident(
                UUID.randomUUID().toString(),
                request.titulo().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                normalizeEnum(request.tipo(), "OUTROS"),
                normalizeEnum(request.severidade(), "P3_MEDIO"),
                initialStatus,
                request.responsavel() == null ? "" : request.responsavel().trim(),
                request.dataOcorrencia() == null ? OffsetDateTime.now() : request.dataOcorrencia(),
                request.dataResolucao(),
                request.impacto() == null ? "" : request.impacto().trim(),
                request.causaRaiz() == null ? "" : request.causaRaiz().trim(),
                request.acoesCorrativas() == null ? "" : request.acoesCorrativas().trim(),
                historico, OffsetDateTime.now(), dono);
        List<Incident> all = new ArrayList<>(loadIncidents());
        all.add(created);
        saveIncidents(all);
        log.info("Incidente criado: id={}, titulo={}, severidade={}, criadoPor={}", created.id(), created.titulo(), created.severidade(), dono);
        return created;
    }

    public Incident createIncident(CreateIncidentRequest request) throws IOException {
        return createIncident(request, null);
    }

    public ImportCsvResponse importIncidentsCsv(ImportIncidentsCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank())
            throw new IllegalArgumentException("CSV e obrigatorio.");
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("titulo")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 1) { ignorados++; continue; }
            try {
                createIncident(new CreateIncidentRequest(
                        stripQuotes(cols[0]),
                        cols.length > 1 ? stripQuotes(cols[1]) : "",
                        cols.length > 2 ? stripQuotes(cols[2]) : "OUTROS",
                        cols.length > 3 ? stripQuotes(cols[3]) : "P3_MEDIO",
                        cols.length > 4 ? stripQuotes(cols[4]) : "ABERTO",
                        cols.length > 5 ? stripQuotes(cols[5]) : "",
                        cols.length > 6 ? parseDate(cols[6]) : null,
                        cols.length > 7 ? parseDate(cols[7]) : null,
                        cols.length > 8 ? stripQuotes(cols[8]) : "",
                        cols.length > 9 ? stripQuotes(cols[9]) : "",
                        cols.length > 10 ? stripQuotes(cols[10]) : ""));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public Incident updateIncident(String incidentId, CreateIncidentRequest request) throws IOException {
        validateIncidentRequest(request);
        List<Incident> all = new ArrayList<>(loadIncidents());
        for (int i = 0; i < all.size(); i++) {
            Incident current = all.get(i);
            if (current.id().equals(incidentId)) {
                String newStatus = normalizeEnum(request.status(), "ABERTO");
                List<IncidentHistoryEvent> historico = new ArrayList<>(safeIncidentHistory(current.historico()));
                if (!newStatus.equals(current.status())) {
                    historico.add(new IncidentHistoryEvent(UUID.randomUUID().toString(),
                            "STATUS", "Status alterado.", current.status(), newStatus, OffsetDateTime.now()));
                }
                Incident updated = new Incident(
                        current.id(),
                        request.titulo().trim(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        normalizeEnum(request.tipo(), "OUTROS"),
                        normalizeEnum(request.severidade(), "P3_MEDIO"),
                        newStatus,
                        request.responsavel() == null ? "" : request.responsavel().trim(),
                        request.dataOcorrencia() == null ? current.dataOcorrencia() : request.dataOcorrencia(),
                        request.dataResolucao(),
                        request.impacto() == null ? "" : request.impacto().trim(),
                        request.causaRaiz() == null ? "" : request.causaRaiz().trim(),
                        request.acoesCorrativas() == null ? "" : request.acoesCorrativas().trim(),
                        historico, current.criadoEm(), current.criadoPor());
                all.set(i, updated);
                saveIncidents(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Incidente nao encontrado.");
    }

    public void deleteIncident(String incidentId) throws IOException {
        List<Incident> all = new ArrayList<>(loadIncidents());
        boolean removed = all.removeIf(i -> i.id().equals(incidentId));
        if (!removed) throw new IllegalArgumentException("Incidente nao encontrado.");
        saveIncidents(all);
    }

    public Incident transferIncidentDono(String id, String novoDono, String username, String role) throws IOException {
        if (novoDono == null || novoDono.isBlank()) throw new IllegalArgumentException("Novo dono e obrigatorio.");
        List<Incident> all = new ArrayList<>(loadIncidents());
        for (int i = 0; i < all.size(); i++) {
            Incident item = all.get(i);
            if (item.id().equals(id)) {
                boolean isOwner = item.criadoPor() == null || username.equalsIgnoreCase(item.criadoPor());
                if (!isOwner && !"ADMIN".equals(role))
                    throw new IllegalArgumentException("Sem permissao para transferir este incidente.");
                Incident updated = new Incident(item.id(), item.titulo(), item.descricao(), item.tipo(),
                        item.severidade(), item.status(), item.responsavel(), item.dataOcorrencia(),
                        item.dataResolucao(), item.impacto(), item.causaRaiz(), item.acoesCorrativas(),
                        item.historico(), item.criadoEm(), novoDono.trim());
                all.set(i, updated);
                saveIncidents(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Incidente nao encontrado.");
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<Incident> loadIncidents() throws IOException {
        return jsonStore.readList(incidentsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public void saveIncidents(List<Incident> incidents) throws IOException {
        jsonStore.writeList(incidentsPath, incidents);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateIncidentRequest(CreateIncidentRequest request) {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do incidente e obrigatorio.");
    }

    private Incident normalizeIncident(Incident i) {
        if (i.historico() != null) return i;
        return new Incident(i.id(), i.titulo(), i.descricao(), i.tipo(), i.severidade(), i.status(),
                i.responsavel(), i.dataOcorrencia(), i.dataResolucao(), i.impacto(),
                i.causaRaiz(), i.acoesCorrativas(), new ArrayList<>(), i.criadoEm(), i.criadoPor());
    }

    private List<IncidentHistoryEvent> safeIncidentHistory(List<IncidentHistoryEvent> history) {
        return history == null ? new ArrayList<>() : new ArrayList<>(history);
    }

    private static String normalizeEnum(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toUpperCase();
    }

    // ── CSV helpers ───────────────────────────────────────────────────────────

    private static String[] splitCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) { columns.add(current.toString()); current.setLength(0); continue; }
            current.append(c);
        }
        columns.add(current.toString());
        return columns.toArray(String[]::new);
    }

    private static String stripQuotes(String value) {
        if (value == null) return "";
        String out = value.trim();
        if (out.startsWith("\"") && out.endsWith("\"") && out.length() >= 2)
            out = out.substring(1, out.length() - 1).trim();
        return out;
    }

    private static OffsetDateTime parseDate(String value) {
        String raw = stripQuotes(value);
        if (raw.isBlank()) return null;
        java.time.LocalDate d = java.time.LocalDate.parse(raw);
        return d.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
    }
}
