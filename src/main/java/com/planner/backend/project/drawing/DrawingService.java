package com.planner.backend.project.drawing;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DrawingService {

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path drawingsPath;

    public DrawingService(FileJsonStore jsonStore, @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore    = jsonStore;
        this.drawingsPath = java.nio.file.Path.of(dataDir, "drawings.json");
        ensureFile();
    }

    private void ensureFile() {
        try {
            if (Files.exists(drawingsPath)) return;
            java.nio.file.Path parent = drawingsPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(drawingsPath, "[]", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar drawings.json", ex);
        }
    }

    public List<DrawingRecord> listDrawings(String username) throws IOException {
        List<DrawingRecord> all = load();
        if (username == null || username.isBlank()) return all;
        return all.stream()
                .filter(d -> username.equalsIgnoreCase(d.criadoPor()))
                .sorted((a, b) -> {
                    if (b.atualizadoEm() == null) return -1;
                    if (a.atualizadoEm() == null) return  1;
                    return b.atualizadoEm().compareTo(a.atualizadoEm());
                })
                .toList();
    }

    public DrawingRecord getDrawing(String id, String username) throws IOException {
        return load().stream()
                .filter(d -> id.equals(d.id()) && username.equalsIgnoreCase(d.criadoPor()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Desenho não encontrado."));
    }

    public DrawingRecord createDrawing(CreateDrawingRequest req, String username) throws IOException {
        if (req == null || req.nome() == null || req.nome().isBlank())
            throw new IllegalArgumentException("Nome do desenho é obrigatório.");
        String id = java.util.UUID.randomUUID().toString();
        DrawingRecord rec = new DrawingRecord(
                id, req.nome().trim(),
                "{\"shapes\":[],\"background\":\"#ffffff\"}",
                normalizeFolder(req.pasta()),
                username, OffsetDateTime.now(), OffsetDateTime.now());
        List<DrawingRecord> all = new ArrayList<>(load());
        all.add(0, rec);
        save(all);
        return rec;
    }

    public DrawingRecord updateDrawing(String id, UpdateDrawingRequest req, String username) throws IOException {
        List<DrawingRecord> all = new ArrayList<>(load());
        for (int i = 0; i < all.size(); i++) {
            DrawingRecord d = all.get(i);
            if (id.equals(d.id()) && username.equalsIgnoreCase(d.criadoPor())) {
                DrawingRecord updated = new DrawingRecord(
                        d.id(),
                        req.nome() != null ? req.nome().trim() : d.nome(),
                        req.data() != null ? req.data() : d.data(),
                        req.pasta() != null ? normalizeFolder(req.pasta()) : normalizeFolder(d.pasta()),
                        d.criadoPor(), d.criadoEm(), OffsetDateTime.now());
                all.set(i, updated);
                save(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Desenho não encontrado.");
    }

    public void deleteDrawing(String id, String username) throws IOException {
        List<DrawingRecord> all = new ArrayList<>(load());
        boolean removed = all.removeIf(d -> id.equals(d.id()) && username.equalsIgnoreCase(d.criadoPor()));
        if (!removed) throw new IllegalArgumentException("Desenho não encontrado.");
        save(all);
    }

    private List<DrawingRecord> load() throws IOException {
        return jsonStore.readList(drawingsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private String normalizeFolder(String folder) {
        if (folder == null) return "";
        return folder.trim()
                .replace("\\", "/")
                .replaceAll("/{2,}", "/")
                .replaceAll("^/|/$", "");
    }

    private void save(List<DrawingRecord> list) throws IOException {
        jsonStore.writeList(drawingsPath, list);
    }
}
