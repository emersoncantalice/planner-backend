package com.planner.backend.project.monitoring;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RiskService {

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path globalRisksPath;

    public RiskService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.globalRisksPath = java.nio.file.Path.of(dataDir, "risks.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(globalRisksPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de risks.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<GlobalRisk> listGlobalRisks() throws IOException {
        List<GlobalRisk> all = loadGlobalRisks();
        List<GlobalRisk> normalized = new ArrayList<>(all.size());
        for (GlobalRisk risk : all) normalized.add(normalizeGlobalRisk(risk));
        return normalized;
    }

    public GlobalRisk createGlobalRisk(CreateGlobalRiskRequest request, String criadoPor) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do apontamento de risco e obrigatorio.");
        if (request.dataFim() == null)
            throw new IllegalArgumentException("Data fim do apontamento de risco e obrigatoria.");
        String status = request.status() == null || request.status().isBlank()
                ? "PLANO_ACAO" : request.status().trim().toUpperCase();
        validateRiskStatus(status);
        String dono = (criadoPor == null || criadoPor.isBlank()) ? null : criadoPor.trim();
        GlobalRisk created = new GlobalRisk(
                UUID.randomUUID().toString(),
                request.titulo().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                request.planoAcao() == null ? "" : request.planoAcao().trim(),
                request.dataFim(), status,
                request.responsavel() == null ? null : request.responsavel().trim(),
                new ArrayList<>(), OffsetDateTime.now(), dono);
        List<GlobalRiskHistoryEvent> historico = new ArrayList<>(safeHistory(created.historico()));
        historico.add(historyEvent("CRIACAO", "Apontamento criado.", null, status, null, request.dataFim(), null));
        created = new GlobalRisk(created.id(), created.titulo(), created.descricao(), created.planoAcao(),
                created.dataFim(), created.status(), created.responsavel(), historico,
                created.criadoEm(), created.criadoPor());
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        all.add(created);
        saveGlobalRisks(all);
        return created;
    }

    public GlobalRisk createGlobalRisk(CreateGlobalRiskRequest request) throws IOException {
        return createGlobalRisk(request, null);
    }

    public GlobalRisk updateGlobalRiskStatus(String riskId, UpdateGlobalRiskStatusRequest request) throws IOException {
        if (request == null || request.status() == null || request.status().isBlank())
            throw new IllegalArgumentException("Status do risco e obrigatorio.");
        String nextStatus = request.status().trim().toUpperCase();
        validateRiskStatus(nextStatus);
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        for (int i = 0; i < all.size(); i++) {
            GlobalRisk r = all.get(i);
            if (r.id().equals(riskId)) {
                List<GlobalRiskHistoryEvent> historico = new ArrayList<>(safeHistory(r.historico()));
                historico.add(historyEvent("STATUS", "Status alterado.", r.status(), nextStatus, r.dataFim(), r.dataFim(), null));
                GlobalRisk updated = new GlobalRisk(r.id(), r.titulo(), r.descricao(), r.planoAcao(),
                        r.dataFim(), nextStatus, r.responsavel(), historico, r.criadoEm(), r.criadoPor());
                all.set(i, updated);
                saveGlobalRisks(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Apontamento de risco nao encontrado.");
    }

    public GlobalRisk updateGlobalRisk(String riskId, CreateGlobalRiskRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do apontamento de risco e obrigatorio.");
        if (request.dataFim() == null)
            throw new IllegalArgumentException("Data fim do apontamento de risco e obrigatoria.");
        String status = request.status() == null || request.status().isBlank()
                ? "PLANO_ACAO" : request.status().trim().toUpperCase();
        validateRiskStatus(status);
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        for (int i = 0; i < all.size(); i++) {
            GlobalRisk r = all.get(i);
            if (r.id().equals(riskId)) {
                List<GlobalRiskHistoryEvent> historico = new ArrayList<>(safeHistory(r.historico()));
                boolean dataAlterada = !sameDateTime(r.dataFim(), request.dataFim());
                if (dataAlterada) {
                    historico.add(historyEvent("AJUSTE_DATA", "Data de vencimento ajustada na edicao.",
                            r.status(), status, r.dataFim(), request.dataFim(), null));
                }
                GlobalRisk updated = new GlobalRisk(r.id(),
                        request.titulo().trim(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        request.planoAcao() == null ? "" : request.planoAcao().trim(),
                        request.dataFim(), status,
                        request.responsavel() == null ? r.responsavel() : request.responsavel().trim(),
                        historico, r.criadoEm(), r.criadoPor());
                all.set(i, updated);
                saveGlobalRisks(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Apontamento de risco nao encontrado.");
    }

    public void deleteGlobalRisk(String riskId) throws IOException {
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        boolean removed = all.removeIf(r -> r.id().equals(riskId));
        if (!removed) throw new IllegalArgumentException("Apontamento de risco nao encontrado.");
        saveGlobalRisks(all);
    }

    public GlobalRisk transferGlobalRiskDono(String id, String novoDono, String username, String role) throws IOException {
        if (novoDono == null || novoDono.isBlank()) throw new IllegalArgumentException("Novo dono e obrigatorio.");
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        for (int i = 0; i < all.size(); i++) {
            GlobalRisk r = all.get(i);
            if (r.id().equals(id)) {
                boolean isOwner = r.criadoPor() == null || username.equalsIgnoreCase(r.criadoPor());
                if (!isOwner && !"ADMIN".equals(role))
                    throw new IllegalArgumentException("Sem permissao para transferir este risco.");
                GlobalRisk updated = new GlobalRisk(r.id(), r.titulo(), r.descricao(), r.planoAcao(),
                        r.dataFim(), r.status(), r.responsavel(), r.historico(), r.criadoEm(), novoDono.trim());
                all.set(i, updated);
                saveGlobalRisks(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Apontamento de risco nao encontrado.");
    }

    public GlobalRisk postponeGlobalRisk(String riskId, PostponeGlobalRiskRequest request) throws IOException {
        if (request == null || request.novaDataFim() == null)
            throw new IllegalArgumentException("Nova data de vencimento e obrigatoria para adiamento.");
        if (request.motivo() == null || request.motivo().isBlank())
            throw new IllegalArgumentException("Motivo do adiamento e obrigatorio.");
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        for (int i = 0; i < all.size(); i++) {
            GlobalRisk r = all.get(i);
            if (r.id().equals(riskId)) {
                if (!request.novaDataFim().isAfter(r.dataFim()))
                    throw new IllegalArgumentException("A nova data deve ser posterior a data atual para caracterizar adiamento.");
                List<GlobalRiskHistoryEvent> historico = new ArrayList<>(safeHistory(r.historico()));
                historico.add(historyEvent("ADIAMENTO", "Prazo adiado.",
                        r.status(), r.status(), r.dataFim(), request.novaDataFim(), request.motivo().trim()));
                GlobalRisk updated = new GlobalRisk(r.id(), r.titulo(), r.descricao(), r.planoAcao(),
                        request.novaDataFim(), r.status(), r.responsavel(), historico, r.criadoEm(), r.criadoPor());
                all.set(i, updated);
                saveGlobalRisks(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Apontamento de risco nao encontrado.");
    }

    public ImportCsvResponse importGlobalRisksCsv(ImportRisksCsvRequest request) throws IOException {
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
            if (cols.length < 2) { ignorados++; continue; }
            try {
                String titulo     = stripQuotes(cols[0]);
                String descricao  = cols.length > 1 ? stripQuotes(cols[1]) : "";
                String planoAcao  = cols.length > 2 ? stripQuotes(cols[2]) : "";
                String statusRaw  = cols.length > 3 ? stripQuotes(cols[3]).toUpperCase(Locale.ROOT) : "PLANO_ACAO";
                String dataFimRaw = cols.length > 4 ? stripQuotes(cols[4]) : "";
                List<String> validStatuses = List.of("PLANO_ACAO", "DESENVOLVIMENTO", "ENTREGA", "CONCLUIDO");
                String status = validStatuses.contains(statusRaw) ? statusRaw : "PLANO_ACAO";
                OffsetDateTime dataFim;
                if (dataFimRaw.isBlank()) {
                    dataFim = OffsetDateTime.now().plusDays(30);
                } else {
                    try {
                        dataFim = LocalDate.parse(dataFimRaw.substring(0, 10))
                                .atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime();
                    } catch (Exception ex) {
                        dataFim = OffsetDateTime.now().plusDays(30);
                    }
                }
                String responsavel = cols.length > 5 ? stripQuotes(cols[5]) : "";
                createGlobalRisk(new CreateGlobalRiskRequest(titulo, descricao, planoAcao, status, dataFim, responsavel));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<GlobalRisk> loadGlobalRisks() throws IOException {
        return jsonStore.readList(globalRisksPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public void saveGlobalRisks(List<GlobalRisk> risks) throws IOException {
        jsonStore.writeList(globalRisksPath, risks);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateRiskStatus(String status) {
        if (!status.equals("PLANO_ACAO") && !status.equals("DESENVOLVIMENTO")
                && !status.equals("ENTREGA") && !status.equals("CONCLUIDO"))
            throw new IllegalArgumentException("Status invalido para risco.");
    }

    private GlobalRisk normalizeGlobalRisk(GlobalRisk risk) {
        if (risk == null) return null;
        return new GlobalRisk(risk.id(), risk.titulo(), risk.descricao(), risk.planoAcao(),
                risk.dataFim(), risk.status(), risk.responsavel(),
                safeHistory(risk.historico()), risk.criadoEm(), risk.criadoPor());
    }

    private List<GlobalRiskHistoryEvent> safeHistory(List<GlobalRiskHistoryEvent> history) {
        return history == null ? new ArrayList<>() : history;
    }

    private GlobalRiskHistoryEvent historyEvent(String tipo, String descricao, String statusAnterior,
            String statusNovo, OffsetDateTime dataFimAnterior, OffsetDateTime dataFimNova, String motivo) {
        return new GlobalRiskHistoryEvent(UUID.randomUUID().toString(), tipo, descricao,
                statusAnterior, statusNovo, dataFimAnterior, dataFimNova, motivo, OffsetDateTime.now());
    }

    private boolean sameDateTime(OffsetDateTime a, OffsetDateTime b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.isEqual(b);
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
