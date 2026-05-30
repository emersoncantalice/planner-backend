package com.planner.backend.project.consulting;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConsultingService {

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path consultanciesPath;
    private final java.nio.file.Path focalPointsPath;

    public ConsultingService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.consultanciesPath = java.nio.file.Path.of(dataDir, "consultancies.json");
        this.focalPointsPath   = java.nio.file.Path.of(dataDir, "focal-points.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(consultanciesPath);
            ensureFile(focalPointsPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de consultancies.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    // ── Consultancies ─────────────────────────────────────────────────────────

    public List<Consultancy> listConsultancies() throws IOException {
        List<Consultancy> all = loadConsultancies();
        List<Consultancy> normalized = new ArrayList<>(all.size());
        for (Consultancy c : all) normalized.add(normalizeConsultancy(c));
        return normalized;
    }

    public Consultancy createConsultancy(CreateConsultancyRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome do prestador de servico e obrigatorio.");
        List<Consultancy> all = new ArrayList<>(loadConsultancies());
        boolean duplicate = all.stream().anyMatch(c -> c.nome().equalsIgnoreCase(request.nome().trim()));
        if (duplicate) throw new IllegalArgumentException("Ja existe prestador de servico com esse nome.");
        Consultancy created = new Consultancy(
                UUID.randomUUID().toString(),
                request.nome().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                resolveTelefone(request),
                resolveEmail(request),
                "",
                OffsetDateTime.now());
        all.add(created);
        saveConsultancies(all);
        return created;
    }

    public ImportCsvResponse importConsultanciesCsv(ImportConsultanciesCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank())
            throw new IllegalArgumentException("CSV e obrigatorio.");
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("telefone")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 4) { ignorados++; continue; }
            try {
                createConsultancy(new CreateConsultancyRequest(
                        stripQuotes(cols[0]), stripQuotes(cols[1]),
                        stripQuotes(cols[2]), stripQuotes(cols[3]), ""));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public Consultancy updateConsultancy(String consultancyId, CreateConsultancyRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome do prestador de servico e obrigatorio.");
        List<Consultancy> all = new ArrayList<>(loadConsultancies());
        for (int i = 0; i < all.size(); i++) {
            Consultancy c = all.get(i);
            if (c.id().equals(consultancyId)) {
                Consultancy updated = new Consultancy(
                        c.id(), request.nome().trim(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        resolveTelefone(request), resolveEmail(request), "", c.criadoEm());
                all.set(i, updated);
                saveConsultancies(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Prestador de servico nao encontrado.");
    }

    public void deleteConsultancy(String consultancyId) throws IOException {
        List<Consultancy> all = new ArrayList<>(loadConsultancies());
        boolean removed = all.removeIf(c -> c.id().equals(consultancyId));
        if (!removed) throw new IllegalArgumentException("Prestador de servico nao encontrado.");
        saveConsultancies(all);
    }

    // ── Focal Points ──────────────────────────────────────────────────────────

    public List<FocalPoint> listFocalPoints() throws IOException {
        return loadFocalPoints();
    }

    public FocalPoint createFocalPoint(CreateFocalPointRequest request) throws IOException {
        validateFocalPointRequest(request);
        List<FocalPoint> all = new ArrayList<>(loadFocalPoints());
        FocalPoint created = new FocalPoint(
                UUID.randomUUID().toString(),
                request.area().trim(),
                request.responsavelPor().trim(),
                request.email().trim(),
                request.telefone().trim(),
                OffsetDateTime.now());
        all.add(created);
        saveFocalPoints(all);
        return created;
    }

    public ImportCsvResponse importFocalPointsCsv(ImportFocalPointsCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank())
            throw new IllegalArgumentException("CSV e obrigatorio.");
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("responsavel")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 4) { ignorados++; continue; }
            try {
                createFocalPoint(new CreateFocalPointRequest(
                        stripQuotes(cols[0]), stripQuotes(cols[1]),
                        stripQuotes(cols[2]), stripQuotes(cols[3])));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public FocalPoint updateFocalPoint(String focalPointId, CreateFocalPointRequest request) throws IOException {
        validateFocalPointRequest(request);
        List<FocalPoint> all = new ArrayList<>(loadFocalPoints());
        for (int i = 0; i < all.size(); i++) {
            FocalPoint current = all.get(i);
            if (current.id().equals(focalPointId)) {
                FocalPoint updated = new FocalPoint(
                        current.id(), request.area().trim(), request.responsavelPor().trim(),
                        request.email().trim(), request.telefone().trim(), current.criadoEm());
                all.set(i, updated);
                saveFocalPoints(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Ponto focal nao encontrado.");
    }

    public void deleteFocalPoint(String focalPointId) throws IOException {
        List<FocalPoint> all = new ArrayList<>(loadFocalPoints());
        boolean removed = all.removeIf(p -> p.id().equals(focalPointId));
        if (!removed) throw new IllegalArgumentException("Ponto focal nao encontrado.");
        saveFocalPoints(all);
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<Consultancy> loadConsultancies() throws IOException {
        return jsonStore.readList(consultanciesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveConsultancies(List<Consultancy> list) throws IOException {
        jsonStore.writeList(consultanciesPath, list);
    }

    private List<FocalPoint> loadFocalPoints() throws IOException {
        return jsonStore.readList(focalPointsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveFocalPoints(List<FocalPoint> list) throws IOException {
        jsonStore.writeList(focalPointsPath, list);
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private void validateFocalPointRequest(CreateFocalPointRequest request) {
        if (request == null || request.area() == null || request.area().isBlank())
            throw new IllegalArgumentException("Area do ponto focal e obrigatoria.");
        if (request.responsavelPor() == null || request.responsavelPor().isBlank())
            throw new IllegalArgumentException("Responsavel por e obrigatorio.");
        if (request.email() == null || request.email().isBlank())
            throw new IllegalArgumentException("Email do ponto focal e obrigatorio.");
        if (request.telefone() == null || request.telefone().isBlank())
            throw new IllegalArgumentException("Telefone do ponto focal e obrigatorio.");
    }

    private Consultancy normalizeConsultancy(Consultancy consultancy) {
        if (consultancy == null) return null;
        String telefone = consultancy.telefone();
        String email = consultancy.email();
        String legacyContato = consultancy.contato();
        if ((telefone == null || telefone.isBlank()) && legacyContato != null && !legacyContato.isBlank()) {
            if (legacyContato.contains("@")) email = legacyContato.trim();
            else telefone = legacyContato.trim();
        }
        return new Consultancy(consultancy.id(), consultancy.nome(), consultancy.descricao(),
                telefone == null ? "" : telefone.trim(), email == null ? "" : email.trim(), "", consultancy.criadoEm());
    }

    private String resolveTelefone(CreateConsultancyRequest request) {
        String telefone = request.telefone();
        if ((telefone == null || telefone.isBlank()) && request.contato() != null
                && !request.contato().isBlank() && !request.contato().contains("@"))
            telefone = request.contato();
        return telefone == null ? "" : telefone.trim();
    }

    private String resolveEmail(CreateConsultancyRequest request) {
        String email = request.email();
        if ((email == null || email.isBlank()) && request.contato() != null
                && !request.contato().isBlank() && request.contato().contains("@"))
            email = request.contato();
        return email == null ? "" : email.trim();
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
}
