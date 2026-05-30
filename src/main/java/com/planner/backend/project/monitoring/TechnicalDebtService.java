package com.planner.backend.project.monitoring;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TechnicalDebtService {
    private static final Logger log = LoggerFactory.getLogger(TechnicalDebtService.class);

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path technicalDebtsPath;

    public TechnicalDebtService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.technicalDebtsPath = java.nio.file.Path.of(dataDir, "technical-debts.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(technicalDebtsPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de technical-debts.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    public List<TechnicalDebt> listTechnicalDebts() throws IOException {
        return loadTechnicalDebts().stream().map(this::normalizeDebt).toList();
    }

    public TechnicalDebt createTechnicalDebt(CreateTechnicalDebtRequest request, String criadoPor) throws IOException {
        validateTechnicalDebtRequest(request);
        String initialStatus = normalizeEnum(request.status(), "IDENTIFICADO");
        String dono = (criadoPor == null || criadoPor.isBlank()) ? null : criadoPor.trim();
        List<TechnicalDebtHistoryEvent> historico = new ArrayList<>();
        historico.add(new TechnicalDebtHistoryEvent(UUID.randomUUID().toString(),
                "CRIACAO", "Debito tecnico registrado.", null, initialStatus, OffsetDateTime.now()));
        TechnicalDebt created = new TechnicalDebt(
                UUID.randomUUID().toString(),
                request.titulo().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                normalizeEnum(request.categoria(), "OUTROS"),
                normalizeEnum(request.impacto(), "MEDIO"),
                request.esforcoEstimado() == null || request.esforcoEstimado() < 0 ? 0 : request.esforcoEstimado(),
                normalizeEnum(request.prioridade(), "MEDIA"),
                initialStatus,
                request.responsavel() == null ? "" : request.responsavel().trim(),
                request.projetoRef() == null ? "" : request.projetoRef().trim(),
                request.dataAlvo(),
                null,
                historico, OffsetDateTime.now(), dono);
        List<TechnicalDebt> all = new ArrayList<>(loadTechnicalDebts());
        all.add(created);
        saveTechnicalDebts(all);
        log.info("Debito tecnico criado: id={}, titulo={}, prioridade={}, criadoPor={}",
                created.id(), created.titulo(), created.prioridade(), dono);
        return created;
    }

    public TechnicalDebt updateTechnicalDebt(String debtId, CreateTechnicalDebtRequest request) throws IOException {
        validateTechnicalDebtRequest(request);
        List<TechnicalDebt> all = new ArrayList<>(loadTechnicalDebts());
        for (int i = 0; i < all.size(); i++) {
            TechnicalDebt current = all.get(i);
            if (current.id().equals(debtId)) {
                String newStatus = normalizeEnum(request.status(), "IDENTIFICADO");
                OffsetDateTime resolvidoEm = current.resolvidoEm();
                if ("RESOLVIDO".equals(newStatus) && resolvidoEm == null) {
                    resolvidoEm = OffsetDateTime.now();
                } else if (!"RESOLVIDO".equals(newStatus)) {
                    resolvidoEm = null;
                }
                List<TechnicalDebtHistoryEvent> historico = new ArrayList<>(safeDebtHistory(current.historico()));
                if (!newStatus.equals(current.status())) {
                    historico.add(new TechnicalDebtHistoryEvent(UUID.randomUUID().toString(),
                            "STATUS", "Status alterado.", current.status(), newStatus, OffsetDateTime.now()));
                }
                TechnicalDebt updated = new TechnicalDebt(
                        current.id(),
                        request.titulo().trim(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        normalizeEnum(request.categoria(), "OUTROS"),
                        normalizeEnum(request.impacto(), "MEDIO"),
                        request.esforcoEstimado() == null || request.esforcoEstimado() < 0 ? 0 : request.esforcoEstimado(),
                        normalizeEnum(request.prioridade(), "MEDIA"),
                        newStatus,
                        request.responsavel() == null ? "" : request.responsavel().trim(),
                        request.projetoRef() == null ? "" : request.projetoRef().trim(),
                        request.dataAlvo(),
                        resolvidoEm,
                        historico, current.criadoEm(), current.criadoPor());
                all.set(i, updated);
                saveTechnicalDebts(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Debito tecnico nao encontrado.");
    }

    public void deleteTechnicalDebt(String debtId) throws IOException {
        List<TechnicalDebt> all = new ArrayList<>(loadTechnicalDebts());
        boolean removed = all.removeIf(d -> d.id().equals(debtId));
        if (!removed) throw new IllegalArgumentException("Debito tecnico nao encontrado.");
        saveTechnicalDebts(all);
    }

    public TechnicalDebt transferTechnicalDebtDono(String id, String novoDono, String username, String role) throws IOException {
        if (novoDono == null || novoDono.isBlank()) throw new IllegalArgumentException("Novo dono e obrigatorio.");
        List<TechnicalDebt> all = new ArrayList<>(loadTechnicalDebts());
        for (int i = 0; i < all.size(); i++) {
            TechnicalDebt d = all.get(i);
            if (d.id().equals(id)) {
                boolean isOwner = d.criadoPor() == null || username.equalsIgnoreCase(d.criadoPor());
                if (!isOwner && !"ADMIN".equals(role))
                    throw new IllegalArgumentException("Sem permissao para transferir este debito tecnico.");
                TechnicalDebt updated = new TechnicalDebt(d.id(), d.titulo(), d.descricao(), d.categoria(),
                        d.impacto(), d.esforcoEstimado(), d.prioridade(), d.status(), d.responsavel(),
                        d.projetoRef(), d.dataAlvo(), d.resolvidoEm(), d.historico(), d.criadoEm(), novoDono.trim());
                all.set(i, updated);
                saveTechnicalDebts(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Debito tecnico nao encontrado.");
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<TechnicalDebt> loadTechnicalDebts() throws IOException {
        return jsonStore.readList(technicalDebtsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public void saveTechnicalDebts(List<TechnicalDebt> debts) throws IOException {
        jsonStore.writeList(technicalDebtsPath, debts);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateTechnicalDebtRequest(CreateTechnicalDebtRequest request) {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do debito tecnico e obrigatorio.");
    }

    private TechnicalDebt normalizeDebt(TechnicalDebt d) {
        if (d.historico() != null) return d;
        return new TechnicalDebt(d.id(), d.titulo(), d.descricao(), d.categoria(), d.impacto(),
                d.esforcoEstimado(), d.prioridade(), d.status(), d.responsavel(),
                d.projetoRef(), d.dataAlvo(), d.resolvidoEm(), new ArrayList<>(), d.criadoEm(), d.criadoPor());
    }

    private List<TechnicalDebtHistoryEvent> safeDebtHistory(List<TechnicalDebtHistoryEvent> history) {
        return history == null ? new ArrayList<>() : new ArrayList<>(history);
    }

    private static String normalizeEnum(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toUpperCase();
    }
}
