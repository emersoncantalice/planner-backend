package com.planner.backend.project.tracking;

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
public class AllocationStateService {

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path allocationPaymentsPath;
    private final java.nio.file.Path loPresencePath;
    private final java.nio.file.Path allocationMonthlyStatePath;
    private final java.nio.file.Path allocationPercentPath;
    private final java.nio.file.Path loRealizadoPath;
    private final java.nio.file.Path allocationCursorsPath;
    private final java.nio.file.Path loFavoritosPath;

    public AllocationStateService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.allocationPaymentsPath     = java.nio.file.Path.of(dataDir, "allocation-payments.json");
        this.loPresencePath             = java.nio.file.Path.of(dataDir, "lo-presence.json");
        this.allocationMonthlyStatePath = java.nio.file.Path.of(dataDir, "allocation-monthly-state.json");
        this.allocationPercentPath      = java.nio.file.Path.of(dataDir, "allocation-percent.json");
        this.loRealizadoPath            = java.nio.file.Path.of(dataDir, "lo-realizado.json");
        this.allocationCursorsPath      = java.nio.file.Path.of(dataDir, "allocation-cursors.json");
        this.loFavoritosPath            = java.nio.file.Path.of(dataDir, "lo-favoritos.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(allocationPaymentsPath);
            ensureFile(loPresencePath);
            ensureFile(allocationMonthlyStatePath);
            ensureFile(allocationPercentPath);
            ensureFile(loRealizadoPath);
            ensureFile(allocationCursorsPath);
            ensureFile(loFavoritosPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de tracking.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    // ── Allocation Payments ───────────────────────────────────────────────────

    public List<AllocationPaymentState> listAllocationPayments() throws IOException {
        List<AllocationPaymentState> all = new ArrayList<>(loadAllocationPayments());
        all.sort(Comparator
                .comparing(AllocationPaymentState::allocationId, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(AllocationPaymentState::month));
        return all;
    }

    public AllocationPaymentState upsertAllocationPayment(String allocationId, int month, boolean paid, String username) throws IOException {
        if (allocationId == null || allocationId.isBlank())
            throw new IllegalArgumentException("AllocationId e obrigatorio.");
        if (month < 0 || month > 11)
            throw new IllegalArgumentException("Mes invalido. Use 0..11.");
        String user = username == null ? "" : username;
        AllocationPaymentState updated = new AllocationPaymentState(
                allocationId.trim(), month, paid, OffsetDateTime.now(), user);
        List<AllocationPaymentState> all = new ArrayList<>(loadAllocationPayments());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            AllocationPaymentState curr = all.get(i);
            if (allocationId.trim().equals(curr.allocationId()) && month == curr.month()) {
                all.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(updated);
        saveAllocationPayments(all);
        return updated;
    }

    // ── Lo Presence ───────────────────────────────────────────────────────────

    public List<LoPresenceState> listLoPresence() throws IOException {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(30);
        List<LoPresenceState> all = new ArrayList<>(loadLoPresence());
        List<LoPresenceState> active = all.stream()
                .filter(p -> p != null && p.updatedAt() != null && p.updatedAt().isAfter(cutoff))
                .sorted(Comparator
                        .comparing(LoPresenceState::loId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LoPresenceState::username, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (active.size() != all.size()) saveLoPresence(new ArrayList<>(active));
        return active;
    }

    public LoPresenceState upsertLoPresence(String loId, String username) throws IOException {
        if (loId == null || loId.isBlank()) throw new IllegalArgumentException("LoId e obrigatorio.");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Usuario nao autenticado.");
        String lo = loId.trim();
        String user = username.trim();
        LoPresenceState updated = new LoPresenceState(lo, user, OffsetDateTime.now());
        List<LoPresenceState> all = new ArrayList<>(loadLoPresence());
        all.removeIf(p -> p != null && lo.equals(p.loId()) && user.equalsIgnoreCase(p.username()));
        all.add(updated);
        saveLoPresence(all);
        return updated;
    }

    // ── Allocation Monthly State ──────────────────────────────────────────────

    public List<AllocationMonthlyState> listAllocationMonthlyStates() throws IOException {
        List<AllocationMonthlyState> all = new ArrayList<>(loadAllocationMonthlyStates());
        all.sort(Comparator
                .comparing(AllocationMonthlyState::allocationId, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(AllocationMonthlyState::month));
        return all;
    }

    public AllocationMonthlyState upsertAllocationMonthlyState(
            String allocationId, int month,
            UpdateAllocationMonthlyStateRequest request, String username) throws IOException {
        if (allocationId == null || allocationId.isBlank()) throw new IllegalArgumentException("AllocationId e obrigatorio.");
        if (month < 0 || month > 11) throw new IllegalArgumentException("Mes invalido. Use 0..11.");
        String user = username == null ? "" : username.trim();
        String alloc = allocationId.trim();
        BigDecimal manualValue   = request != null ? request.manualValue() : null;
        BigDecimal manualPercent = request != null ? request.manualPercent() : null;
        AllocationMonthlyState updated = new AllocationMonthlyState(
                alloc, month,
                request != null ? request.canceled() : null,
                manualValue, manualPercent,
                OffsetDateTime.now(), user);
        List<AllocationMonthlyState> all = new ArrayList<>(loadAllocationMonthlyStates());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            AllocationMonthlyState curr = all.get(i);
            if (alloc.equals(curr.allocationId()) && month == curr.month()) {
                all.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(updated);
        saveAllocationMonthlyStates(all);
        return updated;
    }

    // ── Allocation Percent ────────────────────────────────────────────────────

    public List<AllocationPercentConfig> listAllocationPercents() throws IOException {
        List<AllocationPercentConfig> all = new ArrayList<>(loadAllocationPercents());
        all.sort(Comparator.comparing(AllocationPercentConfig::allocationId, Comparator.nullsLast(String::compareTo)));
        return all;
    }

    public AllocationPercentConfig upsertAllocationPercent(String allocationId, BigDecimal percentual, String user) throws IOException {
        if (allocationId == null || allocationId.isBlank())
            throw new IllegalArgumentException("allocationId e obrigatorio.");
        BigDecimal pct = percentual != null ? percentual : BigDecimal.ZERO;
        AllocationPercentConfig updated = new AllocationPercentConfig(
                allocationId.trim(), pct, OffsetDateTime.now(), user);
        List<AllocationPercentConfig> all = new ArrayList<>(loadAllocationPercents());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (allocationId.trim().equals(all.get(i).allocationId())) {
                all.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(updated);
        saveAllocationPercents(all);
        return updated;
    }

    // ── Allocation Cursors ────────────────────────────────────────────────────

    public List<AllocationCursorState> listAllocationCursors(String loId) throws IOException {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(10);
        String filterLo = loId == null ? "" : loId.trim();
        List<AllocationCursorState> all = new ArrayList<>(loadAllocationCursors());
        List<AllocationCursorState> active = all.stream()
                .filter(c -> c != null && c.updatedAt() != null && c.updatedAt().isAfter(cutoff))
                .filter(c -> filterLo.isBlank() || filterLo.equals(c.loId()))
                .sorted(Comparator.comparing(AllocationCursorState::username, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (active.size() != all.size()) saveAllocationCursors(new ArrayList<>(active));
        return active;
    }

    public AllocationCursorState upsertAllocationCursor(UpsertAllocationCursorRequest request, String username) throws IOException {
        if (request == null || request.loId() == null || request.loId().isBlank())
            throw new IllegalArgumentException("LoId e obrigatorio.");
        if (request.x() == null || request.y() == null)
            throw new IllegalArgumentException("Posicao do cursor e obrigatoria.");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Usuario nao autenticado.");
        BigDecimal x = request.x().max(BigDecimal.ZERO).min(BigDecimal.ONE);
        BigDecimal y = request.y().max(BigDecimal.ZERO).min(BigDecimal.ONE);
        String lo   = request.loId().trim();
        String user = username.trim();
        AllocationCursorState updated = new AllocationCursorState(user, lo, x, y, OffsetDateTime.now());
        List<AllocationCursorState> all = new ArrayList<>(loadAllocationCursors());
        all.removeIf(c -> c != null && user.equalsIgnoreCase(c.username()) && lo.equals(c.loId()));
        all.add(updated);
        saveAllocationCursors(all);
        return updated;
    }

    // ── Lo Realizado ──────────────────────────────────────────────────────────

    public List<LoRealizadoConfig> listLoRealizado() throws IOException {
        List<LoRealizadoConfig> all = new ArrayList<>(loadLoRealizado());
        all.sort(Comparator.comparing(LoRealizadoConfig::loId, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(LoRealizadoConfig::month));
        return all;
    }

    public LoRealizadoConfig upsertLoRealizado(String loId, int month, BigDecimal valor, String user) throws IOException {
        if (loId == null || loId.isBlank())
            throw new IllegalArgumentException("loId e obrigatorio.");
        if (month < 0 || month > 11)
            throw new IllegalArgumentException("Mes invalido (0-11).");
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        LoRealizadoConfig updated = new LoRealizadoConfig(loId.trim(), month, v, OffsetDateTime.now(), user);
        List<LoRealizadoConfig> all = new ArrayList<>(loadLoRealizado());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            LoRealizadoConfig curr = all.get(i);
            if (loId.trim().equals(curr.loId()) && month == curr.month()) {
                all.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(updated);
        saveLoRealizado(all);
        return updated;
    }

    // ── LO Favoritos ─────────────────────────────────────────────────────────

    public List<LoFavoriteConfig> listLoFavoritos() throws IOException {
        return new ArrayList<>(loadLoFavoritos());
    }

    public List<LoFavoriteConfig> addLoFavorito(String loId, String username) throws IOException {
        if (loId == null || loId.isBlank())
            throw new IllegalArgumentException("loId e obrigatorio.");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Usuario nao autenticado.");
        String lo   = loId.trim();
        String user = username.trim();
        List<LoFavoriteConfig> all = new ArrayList<>(loadLoFavoritos());
        boolean exists = all.stream().anyMatch(f -> lo.equals(f.loId()) && user.equalsIgnoreCase(f.username()));
        if (!exists) {
            all.add(new LoFavoriteConfig(lo, user, OffsetDateTime.now()));
            saveLoFavoritos(all);
        }
        return all;
    }

    public List<LoFavoriteConfig> removeLoFavorito(String loId, String username) throws IOException {
        if (loId == null || loId.isBlank())
            throw new IllegalArgumentException("loId e obrigatorio.");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Usuario nao autenticado.");
        String lo   = loId.trim();
        String user = username.trim();
        List<LoFavoriteConfig> all = new ArrayList<>(loadLoFavoritos());
        all.removeIf(f -> lo.equals(f.loId()) && user.equalsIgnoreCase(f.username()));
        saveLoFavoritos(all);
        return all;
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    private List<AllocationPaymentState> loadAllocationPayments() throws IOException {
        return jsonStore.readList(allocationPaymentsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAllocationPayments(List<AllocationPaymentState> list) throws IOException {
        jsonStore.writeList(allocationPaymentsPath, list);
    }

    private List<LoPresenceState> loadLoPresence() throws IOException {
        return jsonStore.readList(loPresencePath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveLoPresence(List<LoPresenceState> list) throws IOException {
        jsonStore.writeList(loPresencePath, list);
    }

    private List<AllocationMonthlyState> loadAllocationMonthlyStates() throws IOException {
        return jsonStore.readList(allocationMonthlyStatePath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAllocationMonthlyStates(List<AllocationMonthlyState> list) throws IOException {
        jsonStore.writeList(allocationMonthlyStatePath, list);
    }

    private List<AllocationPercentConfig> loadAllocationPercents() throws IOException {
        return jsonStore.readList(allocationPercentPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAllocationPercents(List<AllocationPercentConfig> list) throws IOException {
        jsonStore.writeList(allocationPercentPath, list);
    }

    private List<LoRealizadoConfig> loadLoRealizado() throws IOException {
        return jsonStore.readList(loRealizadoPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveLoRealizado(List<LoRealizadoConfig> list) throws IOException {
        jsonStore.writeList(loRealizadoPath, list);
    }

    private List<AllocationCursorState> loadAllocationCursors() throws IOException {
        return jsonStore.readList(allocationCursorsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAllocationCursors(List<AllocationCursorState> list) throws IOException {
        jsonStore.writeList(allocationCursorsPath, list);
    }

    private List<LoFavoriteConfig> loadLoFavoritos() throws IOException {
        return jsonStore.readList(loFavoritosPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveLoFavoritos(List<LoFavoriteConfig> list) throws IOException {
        jsonStore.writeList(loFavoritosPath, list);
    }
}
