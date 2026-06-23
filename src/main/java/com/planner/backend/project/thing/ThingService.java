package com.planner.backend.project.thing;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ThingService {

    private static final Set<String> ALLOWED_TYPES = Set.of("texto", "imagem", "query", "codigo", "arquivo");

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path thingsPath;
    private final java.nio.file.Path foldersPath;

    public ThingService(FileJsonStore jsonStore, @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore   = jsonStore;
        this.thingsPath  = java.nio.file.Path.of(dataDir, "things.json");
        this.foldersPath = java.nio.file.Path.of(dataDir, "thing-folders.json");
        ensureFile(thingsPath, "things.json");
        ensureFile(foldersPath, "thing-folders.json");
    }

    private void ensureFile(java.nio.file.Path path, String label) {
        try {
            if (Files.exists(path)) return;
            java.nio.file.Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, "[]", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar " + label, ex);
        }
    }

    public List<ThingRecord> listThings(String username) throws IOException {
        List<ThingRecord> all = load();
        if (username == null || username.isBlank()) return all;
        return all.stream()
                .filter(t -> username.equalsIgnoreCase(t.criadoPor()))
                .sorted((a, b) -> {
                    if (b.atualizadoEm() == null) return -1;
                    if (a.atualizadoEm() == null) return  1;
                    return b.atualizadoEm().compareTo(a.atualizadoEm());
                })
                .toList();
    }

    public ThingRecord getThing(String id, String username) throws IOException {
        return load().stream()
                .filter(t -> id.equals(t.id()) && username.equalsIgnoreCase(t.criadoPor()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item não encontrado."));
    }

    public ThingRecord createThing(CreateThingRequest req, String username) throws IOException {
        if (req == null || req.titulo() == null || req.titulo().isBlank())
            throw new IllegalArgumentException("Título do item é obrigatório.");
        String tipo = normalizeType(req.tipo());
        String id = java.util.UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        ThingRecord rec = new ThingRecord(
                id,
                req.titulo().trim(),
                tipo,
                req.conteudo() != null ? req.conteudo() : "",
                normalizeLanguage(req.linguagem()),
                req.arquivo() != null ? req.arquivo().trim() : "",
                normalizeLabels(req.labels()),
                normalizeFolder(req.pasta()),
                username, now, now);
        List<ThingRecord> all = new ArrayList<>(load());
        all.add(0, rec);
        save(all);
        return rec;
    }

    public ThingRecord updateThing(String id, UpdateThingRequest req, String username) throws IOException {
        List<ThingRecord> all = new ArrayList<>(load());
        for (int i = 0; i < all.size(); i++) {
            ThingRecord t = all.get(i);
            if (id.equals(t.id()) && username.equalsIgnoreCase(t.criadoPor())) {
                ThingRecord updated = new ThingRecord(
                        t.id(),
                        req.titulo() != null && !req.titulo().isBlank() ? req.titulo().trim() : t.titulo(),
                        req.tipo() != null ? normalizeType(req.tipo()) : t.tipo(),
                        req.conteudo() != null ? req.conteudo() : t.conteudo(),
                        req.linguagem() != null ? normalizeLanguage(req.linguagem()) : t.linguagem(),
                        req.arquivo() != null ? req.arquivo().trim() : t.arquivo(),
                        req.labels() != null ? normalizeLabels(req.labels()) : t.labels(),
                        req.pasta() != null ? normalizeFolder(req.pasta()) : normalizeFolder(t.pasta()),
                        t.criadoPor(), t.criadoEm(), OffsetDateTime.now());
                all.set(i, updated);
                save(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Item não encontrado.");
    }

    public void deleteThing(String id, String username) throws IOException {
        List<ThingRecord> all = new ArrayList<>(load());
        boolean removed = all.removeIf(t -> id.equals(t.id()) && username.equalsIgnoreCase(t.criadoPor()));
        if (!removed) throw new IllegalArgumentException("Item não encontrado.");
        save(all);
    }

    // ── Pastas (incluindo pastas vazias) ────────────────────────────────────
    public List<String> listFolders(String username) throws IOException {
        return loadFolders().stream()
                .filter(f -> username == null || username.isBlank() || username.equalsIgnoreCase(f.criadoPor()))
                .map(ThingFolderRecord::pasta)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> createFolder(CreateThingFolderRequest req, String username) throws IOException {
        if (req == null || req.pasta() == null || req.pasta().isBlank())
            throw new IllegalArgumentException("Nome da pasta é obrigatório.");
        String pasta = normalizeFolder(req.pasta());
        if (pasta.isEmpty())
            throw new IllegalArgumentException("Nome da pasta é obrigatório.");

        List<ThingFolderRecord> all = new ArrayList<>(loadFolders());
        boolean exists = all.stream().anyMatch(f ->
                pasta.equals(f.pasta()) && username.equalsIgnoreCase(f.criadoPor()));
        if (!exists) {
            all.add(new ThingFolderRecord(pasta, username));
            saveFolders(all);
        }
        return listFolders(username);
    }

    private List<ThingFolderRecord> loadFolders() throws IOException {
        return jsonStore.readList(foldersPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveFolders(List<ThingFolderRecord> list) throws IOException {
        jsonStore.writeList(foldersPath, list);
    }

    private List<ThingRecord> load() throws IOException {
        return jsonStore.readList(thingsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private String normalizeType(String tipo) {
        String t = tipo == null ? "" : tipo.trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(t))
            throw new IllegalArgumentException("Tipo inválido. Use: texto, imagem, query ou codigo.");
        return t;
    }

    private String normalizeLanguage(String linguagem) {
        return linguagem == null ? "" : linguagem.trim().toLowerCase();
    }

    private List<String> normalizeLabels(List<String> labels) {
        if (labels == null) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        for (String label : labels) {
            if (label == null) continue;
            String clean = label.trim();
            if (!clean.isEmpty()) seen.add(clean);
        }
        return new ArrayList<>(seen);
    }

    private String normalizeFolder(String folder) {
        if (folder == null) return "";
        return folder.trim()
                .replace("\\", "/")
                .replaceAll("/{2,}", "/")
                .replaceAll("^/|/$", "");
    }

    private void save(List<ThingRecord> list) throws IOException {
        jsonStore.writeList(thingsPath, list);
    }
}
