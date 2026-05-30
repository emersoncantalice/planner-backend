package com.planner.backend.project.profile;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path profilesPath;
    private final java.nio.file.Path peoplePath;

    public ProfileService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.profilesPath = java.nio.file.Path.of(dataDir, "profiles.json");
        this.peoplePath   = java.nio.file.Path.of(dataDir, "people.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(profilesPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de profiles.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<Profile> listProfiles() throws IOException {
        return loadProfiles();
    }

    public Profile createProfile(CreateProfileRequest request) throws IOException {
        if (request == null || request.nomePerfil() == null || request.nomePerfil().isBlank()) {
            throw new IllegalArgumentException("Nome do perfil e obrigatorio.");
        }
        if (request.valorHora() == null || request.valorHora().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valor hora nao pode ser negativo.");
        }
        Profile profile = new Profile(
                UUID.randomUUID().toString(),
                request.nomePerfil().trim(),
                request.valorHora(),
                request.debitaLo(),
                OffsetDateTime.now());
        List<Profile> profiles = new ArrayList<>(loadProfiles());
        profiles.add(profile);
        saveProfiles(profiles);
        log.info("Perfil criado: id={}, nome={}, valorHora={}", profile.id(), profile.nomePerfil(), profile.valorHora());
        return profile;
    }

    public ImportCsvResponse importProfilesCsv(ImportProfilesCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("nome")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 2) {
                ignorados++;
                continue;
            }
            String nomePerfil = stripQuotes(cols[0]);
            BigDecimal valorHora = parseDecimal(cols[1]);
            boolean debitaLo = cols.length > 2 ? parseBoolean(cols[2], true) : true;
            try {
                createProfile(new CreateProfileRequest(nomePerfil, valorHora, debitaLo));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public Profile updateProfile(String profileId, CreateProfileRequest request) throws IOException {
        if (request == null || request.nomePerfil() == null || request.nomePerfil().isBlank()) {
            throw new IllegalArgumentException("Nome do perfil e obrigatorio.");
        }
        if (request.valorHora() == null || request.valorHora().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valor hora nao pode ser negativo.");
        }
        List<Profile> all = new ArrayList<>(loadProfiles());
        for (int i = 0; i < all.size(); i++) {
            Profile p = all.get(i);
            if (p.id().equals(profileId)) {
                Profile updated = new Profile(p.id(), request.nomePerfil().trim(), request.valorHora(), request.debitaLo(), p.criadoEm());
                all.set(i, updated);
                saveProfiles(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Perfil nao encontrado.");
    }

    public void deleteProfile(String profileId) throws IOException {
        List<Profile> all = new ArrayList<>(loadProfiles());
        boolean found = all.stream().anyMatch(p -> p.id().equals(profileId));
        if (!found) throw new IllegalArgumentException("Perfil nao encontrado.");

        long pessoasVinculadas = loadPeople().stream()
                .filter(p -> profileId.equals(p.perfilId()))
                .count();
        if (pessoasVinculadas > 0) {
            throw new IllegalArgumentException(
                    "Perfil em uso por " + pessoasVinculadas + " pessoa(s). Desvincule-as antes de excluir o perfil.");
        }

        all.removeIf(p -> p.id().equals(profileId));
        saveProfiles(all);
    }

    // ── Package-accessible helpers ────────────────────────────────────────────

    public List<Profile> loadProfiles() throws IOException {
        return jsonStore.readList(profilesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public void saveProfiles(List<Profile> profiles) throws IOException {
        jsonStore.writeList(profilesPath, profiles);
    }

    private List<Person> loadPeople() throws IOException {
        return jsonStore.readList(peoplePath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
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
        if (out.startsWith("\"") && out.endsWith("\"") && out.length() >= 2) {
            out = out.substring(1, out.length() - 1).trim();
        }
        return out;
    }

    private static BigDecimal parseDecimal(String value) {
        String raw = stripQuotes(value);
        if (raw.isBlank()) return null;
        String normalized = raw.replace("R$", "").replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }
        try { return new BigDecimal(normalized); } catch (NumberFormatException ex) { return null; }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        String raw = stripQuotes(value).toLowerCase(Locale.ROOT);
        if (raw.isBlank()) return fallback;
        if (raw.equals("1") || raw.equals("true") || raw.equals("sim") || raw.equals("s")) return true;
        if (raw.equals("0") || raw.equals("false") || raw.equals("nao") || raw.equals("n")) return false;
        return fallback;
    }
}
