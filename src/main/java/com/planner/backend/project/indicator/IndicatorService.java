package com.planner.backend.project.indicator;

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
public class IndicatorService {
    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path indicatorsPath;

    public IndicatorService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.indicatorsPath = java.nio.file.Path.of(dataDir, "indicators.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(indicatorsPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de indicators.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    // ── Indicators ────────────────────────────────────────────────────────────

    public List<Indicator> listIndicators() throws IOException {
        return loadIndicators().stream().map(this::normalizeIndicator).toList();
    }

    public Indicator createIndicator(CreateIndicatorRequest request, String criadoPor) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do indicador e obrigatorio.");
        String dono = (criadoPor == null || criadoPor.isBlank()) ? null : criadoPor.trim();
        Indicator created = new Indicator(
                UUID.randomUUID().toString(),
                request.titulo().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                normalizeEnum(request.tipo(), "NEGOCIO"),
                normalizeEnum(request.categoria(), "OUTROS"),
                request.unidade() == null || request.unidade().isBlank() ? "pts" : request.unidade().trim(),
                request.meta(),
                normalizeEnum(request.polaridade(), "MAIOR_MELHOR"),
                normalizeEnum(request.frequencia(), "MENSAL"),
                request.responsavel() == null ? "" : request.responsavel().trim(),
                normalizeEnum(request.status(), "ATIVO"),
                new ArrayList<>(), new ArrayList<>(),
                OffsetDateTime.now(), dono);
        List<Indicator> all = new ArrayList<>(loadIndicators());
        all.add(created);
        saveIndicators(all);
        log.info("Indicador criado: id={}, titulo={}, tipo={}, criadoPor={}",
                created.id(), created.titulo(), created.tipo(), dono);
        return created;
    }

    public Indicator updateIndicator(String indicatorId, CreateIndicatorRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do indicador e obrigatorio.");
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                Indicator updated = new Indicator(
                        curr.id(),
                        request.titulo().trim(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        normalizeEnum(request.tipo(), "NEGOCIO"),
                        normalizeEnum(request.categoria(), "OUTROS"),
                        request.unidade() == null || request.unidade().isBlank() ? "pts" : request.unidade().trim(),
                        request.meta(),
                        normalizeEnum(request.polaridade(), "MAIOR_MELHOR"),
                        normalizeEnum(request.frequencia(), "MENSAL"),
                        request.responsavel() == null ? "" : request.responsavel().trim(),
                        normalizeEnum(request.status(), "ATIVO"),
                        curr.ciclos(), curr.acoes(), curr.criadoEm(), curr.criadoPor());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    public void deleteIndicator(String indicatorId) throws IOException {
        List<Indicator> all = new ArrayList<>(loadIndicators());
        boolean removed = all.removeIf(i -> i.id().equals(indicatorId));
        if (!removed) throw new IllegalArgumentException("Indicador nao encontrado.");
        saveIndicators(all);
    }

    public Indicator transferIndicatorDono(String id, String novoDono, String username, String role) throws IOException {
        if (novoDono == null || novoDono.isBlank()) throw new IllegalArgumentException("Novo dono e obrigatorio.");
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator ind = all.get(i);
            if (ind.id().equals(id)) {
                boolean isOwner = ind.criadoPor() == null || username.equalsIgnoreCase(ind.criadoPor());
                if (!isOwner && !"ADMIN".equals(role))
                    throw new IllegalArgumentException("Sem permissao para transferir este indicador.");
                Indicator updated = new Indicator(ind.id(), ind.titulo(), ind.descricao(), ind.tipo(),
                        ind.categoria(), ind.unidade(), ind.meta(), ind.polaridade(), ind.frequencia(),
                        ind.responsavel(), ind.status(),
                        safeList(ind.ciclos()), safeList(ind.acoes()), ind.criadoEm(), novoDono.trim());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    // ── Cycles ────────────────────────────────────────────────────────────────

    public Indicator addIndicatorCycle(String indicatorId, CreateIndicatorCycleRequest request) throws IOException {
        if (request == null || request.valor() == null)
            throw new IllegalArgumentException("Valor do ciclo e obrigatorio.");
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                List<IndicatorCycle> ciclos = new ArrayList<>(safeList(curr.ciclos()));
                int nextNum = ciclos.stream().mapToInt(IndicatorCycle::numero).max().orElse(0) + 1;
                ciclos.add(new IndicatorCycle(UUID.randomUUID().toString(), nextNum, request.valor(),
                        request.observacao() == null ? "" : request.observacao().trim(),
                        request.dataReferencia() == null ? OffsetDateTime.now() : request.dataReferencia(),
                        OffsetDateTime.now()));
                List<IndicatorAction> acoes = new ArrayList<>(safeList(curr.acoes()));
                java.util.Set<String> toClose = request.acoesConcluidasIds() == null
                        ? java.util.Set.of() : new java.util.HashSet<>(request.acoesConcluidasIds());
                for (int j = 0; j < acoes.size(); j++) {
                    IndicatorAction a = acoes.get(j);
                    if (toClose.contains(a.id()) && "ABERTA".equals(a.status())) {
                        acoes.set(j, new IndicatorAction(a.id(), a.descricao(), a.responsavel(), "CONCLUIDA",
                                a.cicloAberto(), nextNum, a.prazo(), OffsetDateTime.now(), a.criadoEm()));
                    }
                }
                Indicator updated = new Indicator(curr.id(), curr.titulo(), curr.descricao(), curr.tipo(),
                        curr.categoria(), curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, acoes, curr.criadoEm(), curr.criadoPor());
                all.set(i, updated);
                saveIndicators(all);
                log.info("Ciclo adicionado: indicatorId={}, ciclo={}, valor={}", indicatorId, nextNum, request.valor());
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    public Indicator updateIndicatorCycle(String indicatorId, String cycleId, UpdateIndicatorCycleRequest request) throws IOException {
        if (request == null || request.valor() == null)
            throw new IllegalArgumentException("Valor do ciclo e obrigatorio.");
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                List<IndicatorCycle> ciclos = new ArrayList<>(safeList(curr.ciclos()));
                boolean found = false;
                for (int j = 0; j < ciclos.size(); j++) {
                    IndicatorCycle c = ciclos.get(j);
                    if (c.id().equals(cycleId)) {
                        ciclos.set(j, new IndicatorCycle(c.id(), c.numero(), request.valor(),
                                request.observacao() == null ? c.observacao() : request.observacao().trim(),
                                request.dataReferencia() == null ? c.dataReferencia() : request.dataReferencia(),
                                c.criadoEm()));
                        found = true;
                        break;
                    }
                }
                if (!found) throw new IllegalArgumentException("Ciclo nao encontrado.");
                Indicator updated = new Indicator(curr.id(), curr.titulo(), curr.descricao(), curr.tipo(),
                        curr.categoria(), curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, safeList(curr.acoes()), curr.criadoEm(), curr.criadoPor());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    public Indicator deleteIndicatorCycle(String indicatorId, String cycleId) throws IOException {
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                List<IndicatorCycle> ciclos = new ArrayList<>(safeList(curr.ciclos()));
                boolean removed = ciclos.removeIf(c -> c.id().equals(cycleId));
                if (!removed) throw new IllegalArgumentException("Ciclo nao encontrado.");
                Indicator updated = new Indicator(curr.id(), curr.titulo(), curr.descricao(), curr.tipo(),
                        curr.categoria(), curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, safeList(curr.acoes()), curr.criadoEm(), curr.criadoPor());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    public Indicator addIndicatorAction(String indicatorId, CreateIndicatorActionRequest request) throws IOException {
        if (request == null || request.descricao() == null || request.descricao().isBlank())
            throw new IllegalArgumentException("Descricao da acao e obrigatoria.");
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                List<IndicatorCycle> ciclos = safeList(curr.ciclos());
                int currentCycle = ciclos.stream().mapToInt(IndicatorCycle::numero).max().orElse(0);
                List<IndicatorAction> acoes = new ArrayList<>(safeList(curr.acoes()));
                acoes.add(new IndicatorAction(UUID.randomUUID().toString(),
                        request.descricao().trim(),
                        request.responsavel() == null ? "" : request.responsavel().trim(),
                        "ABERTA", currentCycle, null, request.prazo(), null, OffsetDateTime.now()));
                Indicator updated = new Indicator(curr.id(), curr.titulo(), curr.descricao(), curr.tipo(),
                        curr.categoria(), curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, acoes, curr.criadoEm(), curr.criadoPor());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    public Indicator updateIndicatorAction(String indicatorId, String actionId, UpdateIndicatorActionRequest request) throws IOException {
        if (request == null) throw new IllegalArgumentException("Request invalido.");
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                List<IndicatorCycle> ciclos = safeList(curr.ciclos());
                int currentCycle = ciclos.stream().mapToInt(IndicatorCycle::numero).max().orElse(0);
                List<IndicatorAction> acoes = new ArrayList<>(safeList(curr.acoes()));
                boolean found = false;
                for (int j = 0; j < acoes.size(); j++) {
                    IndicatorAction a = acoes.get(j);
                    if (a.id().equals(actionId)) {
                        found = true;
                        String newStatus = request.status() == null || request.status().isBlank()
                                ? a.status() : request.status().trim().toUpperCase();
                        boolean concluding = "CONCLUIDA".equals(newStatus) && !"CONCLUIDA".equals(a.status());
                        acoes.set(j, new IndicatorAction(a.id(),
                                request.descricao() == null || request.descricao().isBlank() ? a.descricao() : request.descricao().trim(),
                                request.responsavel() == null ? a.responsavel() : request.responsavel().trim(),
                                newStatus,
                                a.cicloAberto(),
                                concluding ? currentCycle : a.cicloConcluido(),
                                request.prazo() == null ? a.prazo() : request.prazo(),
                                concluding ? OffsetDateTime.now() : a.concluidoEm(),
                                a.criadoEm()));
                        break;
                    }
                }
                if (!found) throw new IllegalArgumentException("Acao nao encontrada.");
                Indicator updated = new Indicator(curr.id(), curr.titulo(), curr.descricao(), curr.tipo(),
                        curr.categoria(), curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, acoes, curr.criadoEm(), curr.criadoPor());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    public Indicator deleteIndicatorAction(String indicatorId, String actionId) throws IOException {
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                List<IndicatorAction> acoes = new ArrayList<>(safeList(curr.acoes()));
                boolean removed = acoes.removeIf(a -> a.id().equals(actionId));
                if (!removed) throw new IllegalArgumentException("Acao nao encontrada.");
                Indicator updated = new Indicator(curr.id(), curr.titulo(), curr.descricao(), curr.tipo(),
                        curr.categoria(), curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), curr.ciclos(), acoes, curr.criadoEm(), curr.criadoPor());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<Indicator> loadIndicators() throws IOException {
        return jsonStore.readList(indicatorsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public void saveIndicators(List<Indicator> indicators) throws IOException {
        jsonStore.writeList(indicatorsPath, indicators);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Indicator normalizeIndicator(Indicator ind) {
        if (ind.ciclos() != null && ind.acoes() != null) return ind;
        return new Indicator(ind.id(), ind.titulo(), ind.descricao(), ind.tipo(), ind.categoria(),
                ind.unidade(), ind.meta(), ind.polaridade(), ind.frequencia(), ind.responsavel(), ind.status(),
                ind.ciclos() == null ? new ArrayList<>() : ind.ciclos(),
                ind.acoes() == null ? new ArrayList<>() : ind.acoes(),
                ind.criadoEm(), ind.criadoPor());
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static String normalizeEnum(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toUpperCase();
    }
}
