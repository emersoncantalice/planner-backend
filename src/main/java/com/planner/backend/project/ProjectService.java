package com.planner.backend.project;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.application.port.ProjectStorePort;
import com.planner.backend.project.monitoring.IncidentService;
import com.planner.backend.project.monitoring.RiskService;
import com.planner.backend.project.monitoring.TechnicalDebtService;
import com.planner.backend.project.indicator.IndicatorService;
import com.planner.backend.project.budget.BudgetLineService;
import com.planner.backend.project.profile.ProfileService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ProjectService {
    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private final ProjectStorePort projectStorePort;
    private final java.nio.file.Path ganttConfigsPath;
    private final java.nio.file.Path projectBudgetsPath;
    private final com.planner.backend.auth.FileJsonStore jsonStore;
    private final ProfileService profileService;
    private final RiskService riskService;
    private final IncidentService incidentService;
    private final TechnicalDebtService technicalDebtService;
    private final IndicatorService indicatorService;
    private final BudgetLineService budgetLineService;

    public ProjectService(
            ProjectStorePort projectStorePort,
            com.planner.backend.auth.FileJsonStore jsonStore,
            ProfileService profileService,
            RiskService riskService,
            IncidentService incidentService,
            TechnicalDebtService technicalDebtService,
            IndicatorService indicatorService,
            BudgetLineService budgetLineService,
            @org.springframework.beans.factory.annotation.Value("${planner.data-dir:data}") String dataDir) {
        this.projectStorePort = projectStorePort;
        this.jsonStore = jsonStore;
        this.profileService = profileService;
        this.riskService = riskService;
        this.incidentService = incidentService;
        this.technicalDebtService = technicalDebtService;
        this.indicatorService = indicatorService;
        this.budgetLineService = budgetLineService;
        this.ganttConfigsPath   = java.nio.file.Path.of(dataDir, "gantt-configs.json");
        this.projectBudgetsPath = java.nio.file.Path.of(dataDir, "project-budgets.json");
        ensureDataFiles();
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    public List<ProjectRecord> list(String username, String role) throws IOException {
        List<ProjectRecord> all = load();
        if ("ADMIN".equals(role)) {
            return all.stream()
                    .filter(p -> !"DRAFT".equals(p.situacao())
                            || p.donoProjeto() == null
                            || username.equalsIgnoreCase(p.donoProjeto()))
                    .toList();
        }
        return all.stream()
                .filter(p -> p.donoProjeto() == null
                        || username.equalsIgnoreCase(p.donoProjeto())
                        || !"DRAFT".equals(p.situacao()))
                .toList();
    }

    public List<ProjectRecord> list() throws IOException { return load(); }

    public ProjectRecord getById(String id) throws IOException {
        return load().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Projeto nao encontrado."));
    }

    public ProjectRecord create(CreateProjectRequest request, String donoProjeto) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome do projeto e obrigatorio.");
        ProjectRecord project = new ProjectRecord(
                UUID.randomUUID().toString(),
                request.nome().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                OffsetDateTime.now(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                "DRAFT", donoProjeto);
        List<ProjectRecord> all = new ArrayList<>(load());
        all.add(project);
        save(all);
        log.info("Projeto criado: id={}, nome={}, dono={}", project.id(), project.nome(), donoProjeto);
        return project;
    }

    public ProjectRecord create(CreateProjectRequest request) throws IOException {
        return create(request, null);
    }

    public ProjectRecord updateSituacao(String projectId, String situacao, String username, String role) throws IOException {
        if (!"DRAFT".equals(situacao) && !"PUBLISHED".equals(situacao))
            throw new IllegalArgumentException("Situacao invalida. Use DRAFT ou PUBLISHED.");
        return update(projectId, p -> {
            boolean isOwner = username.equalsIgnoreCase(p.donoProjeto()) || p.donoProjeto() == null;
            if (!isOwner && !"ADMIN".equals(role))
                throw new IllegalArgumentException("Sem permissao para alterar a situacao deste projeto.");
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(),
                    p.etapas(), p.cronograma(), p.alocacoes(), p.financeiro(),
                    p.riscos(), safeReplanList(p.historicoReplanejamento()), situacao, p.donoProjeto());
        });
    }

    public ProjectRecord updateProject(String projectId, CreateProjectRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome do projeto e obrigatorio.");
        return update(projectId, p -> new ProjectRecord(p.id(),
                request.nome().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                p.criadoEm(), p.etapas(), p.cronograma(), p.alocacoes(), p.financeiro(),
                p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto()));
    }

    public void deleteProject(String projectId) throws IOException {
        List<ProjectRecord> all = new ArrayList<>(load());
        boolean removed = all.removeIf(p -> p.id().equals(projectId));
        if (!removed) throw new IllegalArgumentException("Projeto nao encontrado.");
        save(all);
    }

    public ProjectRecord transferProjectDono(String projectId, String novoDono, String username, String role) throws IOException {
        if (novoDono == null || novoDono.isBlank()) throw new IllegalArgumentException("Novo dono e obrigatorio.");
        List<ProjectRecord> all = new ArrayList<>(load());
        for (int i = 0; i < all.size(); i++) {
            ProjectRecord p = all.get(i);
            if (p.id().equals(projectId)) {
                boolean isOwner = p.donoProjeto() == null || username.equalsIgnoreCase(p.donoProjeto());
                if (!isOwner && !"ADMIN".equals(role))
                    throw new IllegalArgumentException("Sem permissao para transferir este projeto.");
                ProjectRecord updated = new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(),
                        p.etapas(), p.cronograma(), p.alocacoes(), p.financeiro(),
                        p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), novoDono.trim());
                all.set(i, updated);
                save(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Projeto nao encontrado.");
    }

    public ReplaceOwnershipResponse replaceOwnershipReferences(String oldValue, String newValue) throws IOException {
        if (oldValue == null || oldValue.isBlank()) throw new IllegalArgumentException("Valor atual e obrigatorio.");
        if (newValue == null || newValue.isBlank()) throw new IllegalArgumentException("Novo valor e obrigatorio.");
        String oldNorm = oldValue.trim();
        String newNorm = newValue.trim();
        int projetosDonoAtualizados = 0;
        int cronogramasResponsavelAtualizados = 0;
        int linhasOrcamentariasDonoAtualizadas = 0;
        int riscosResponsavelAtualizados = 0;
        int incidentesResponsavelAtualizados = 0;
        int debitosResponsavelAtualizados = 0;
        int indicadoresResponsavelAtualizados = 0;
        int acoesIndicadorResponsavelAtualizadas = 0;

        // Projects
        List<ProjectRecord> projetos = new ArrayList<>(load());
        for (int i = 0; i < projetos.size(); i++) {
            ProjectRecord p = projetos.get(i);
            boolean changed = false;
            String donoProjeto = p.donoProjeto();
            if (matchesValue(donoProjeto, oldNorm)) { donoProjeto = newNorm; projetosDonoAtualizados++; changed = true; }
            List<ScheduleItem> cronograma = new ArrayList<>();
            for (ScheduleItem item : p.cronograma()) {
                String responsavel = item.responsavel();
                if (matchesValue(responsavel, oldNorm)) { responsavel = newNorm; cronogramasResponsavelAtualizados++; changed = true; }
                cronograma.add(new ScheduleItem(item.id(), item.titulo(), item.descricao(),
                        item.inicioPlanejado(), item.fimPlanejado(), item.permiteParalelo(),
                        item.status(), item.ordem(), item.criadoEm(), item.cor(), responsavel));
            }
            if (changed) {
                projetos.set(i, new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(),
                        p.etapas(), cronograma, p.alocacoes(), p.financeiro(),
                        p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), donoProjeto));
            }
        }
        save(projetos);

        // Budget lines
        List<BudgetLine> los = new ArrayList<>(budgetLineService.loadBudgetLines());
        for (int i = 0; i < los.size(); i++) {
            BudgetLine lo = los.get(i);
            if (matchesValue(lo.dono(), oldNorm)) {
                los.set(i, new BudgetLine(lo.id(), lo.codigo(), lo.nome(), lo.ano(), lo.tipo(),
                        lo.centroCusto(), lo.valorTotal(), lo.criadoEm(), lo.situacao(), newNorm));
                linhasOrcamentariasDonoAtualizadas++;
            }
        }
        budgetLineService.saveBudgetLines(los);

        // Global risks
        List<GlobalRisk> riscos = new ArrayList<>(riskService.loadGlobalRisks());
        for (int i = 0; i < riscos.size(); i++) {
            GlobalRisk r = riscos.get(i);
            if (matchesValue(r.responsavel(), oldNorm)) {
                riscos.set(i, new GlobalRisk(r.id(), r.titulo(), r.descricao(), r.planoAcao(),
                        r.dataFim(), r.status(), newNorm, r.historico(), r.criadoEm(), r.criadoPor()));
                riscosResponsavelAtualizados++;
            }
        }
        riskService.saveGlobalRisks(riscos);

        // Incidents
        List<Incident> incidents = new ArrayList<>(incidentService.loadIncidents());
        for (int i = 0; i < incidents.size(); i++) {
            Incident it = incidents.get(i);
            if (matchesValue(it.responsavel(), oldNorm)) {
                incidents.set(i, new Incident(it.id(), it.titulo(), it.descricao(), it.tipo(),
                        it.severidade(), it.status(), newNorm, it.dataOcorrencia(), it.dataResolucao(),
                        it.impacto(), it.causaRaiz(), it.acoesCorrativas(),
                        safeIncidentHistory(it.historico()), it.criadoEm(), it.criadoPor()));
                incidentesResponsavelAtualizados++;
            }
        }
        incidentService.saveIncidents(incidents);

        // Technical debts
        List<TechnicalDebt> debts = new ArrayList<>(technicalDebtService.loadTechnicalDebts());
        for (int i = 0; i < debts.size(); i++) {
            TechnicalDebt d = debts.get(i);
            if (matchesValue(d.responsavel(), oldNorm)) {
                debts.set(i, new TechnicalDebt(d.id(), d.titulo(), d.descricao(), d.categoria(),
                        d.impacto(), d.esforcoEstimado(), d.prioridade(), d.status(), newNorm,
                        d.projetoRef(), d.dataAlvo(), d.resolvidoEm(),
                        safeDebtHistory(d.historico()), d.criadoEm(), d.criadoPor()));
                debitosResponsavelAtualizados++;
            }
        }
        technicalDebtService.saveTechnicalDebts(debts);

        // Indicators
        List<Indicator> indicators = new ArrayList<>(indicatorService.loadIndicators());
        for (int i = 0; i < indicators.size(); i++) {
            Indicator ind = indicators.get(i);
            boolean changed = false;
            String responsavel = ind.responsavel();
            if (matchesValue(responsavel, oldNorm)) { responsavel = newNorm; indicadoresResponsavelAtualizados++; changed = true; }
            List<IndicatorAction> acoes = new ArrayList<>();
            for (IndicatorAction a : safeList(ind.acoes())) {
                String respAcao = a.responsavel();
                if (matchesValue(respAcao, oldNorm)) { respAcao = newNorm; acoesIndicadorResponsavelAtualizadas++; changed = true; }
                acoes.add(new IndicatorAction(a.id(), a.descricao(), respAcao, a.status(),
                        a.cicloAberto(), a.cicloConcluido(), a.prazo(), a.concluidoEm(), a.criadoEm()));
            }
            if (changed) {
                indicators.set(i, new Indicator(ind.id(), ind.titulo(), ind.descricao(), ind.tipo(),
                        ind.categoria(), ind.unidade(), ind.meta(), ind.polaridade(), ind.frequencia(),
                        responsavel, ind.status(), safeList(ind.ciclos()), acoes, ind.criadoEm(), ind.criadoPor()));
            }
        }
        indicatorService.saveIndicators(indicators);

        int total = projetosDonoAtualizados + cronogramasResponsavelAtualizados + linhasOrcamentariasDonoAtualizadas
                + riscosResponsavelAtualizados + incidentesResponsavelAtualizados + debitosResponsavelAtualizados
                + indicadoresResponsavelAtualizados + acoesIndicadorResponsavelAtualizadas;
        return new ReplaceOwnershipResponse(
                projetosDonoAtualizados, cronogramasResponsavelAtualizados, linhasOrcamentariasDonoAtualizadas,
                riscosResponsavelAtualizados, incidentesResponsavelAtualizados, debitosResponsavelAtualizados,
                indicadoresResponsavelAtualizados, acoesIndicadorResponsavelAtualizadas, total);
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    public ProjectRecord addStep(String projectId, CreateStepRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo da etapa e obrigatorio.");
        return update(projectId, p -> {
            List<Step> steps = new ArrayList<>(p.etapas());
            steps.add(new Step(UUID.randomUUID().toString(), request.titulo().trim(), false, null));
            log.info("Etapa adicionada: projetoId={}, titulo={}", projectId, request.titulo());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), steps,
                    p.cronograma(), p.alocacoes(), p.financeiro(), p.riscos(),
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord toggleStep(String projectId, String stepId, ToggleStepRequest request) throws IOException {
        return update(projectId, p -> {
            List<Step> steps = new ArrayList<>();
            boolean found = false;
            for (Step step : p.etapas()) {
                if (step.id().equals(stepId)) {
                    found = true;
                    steps.add(new Step(step.id(), step.titulo(), request.concluido(),
                            request.concluido() ? OffsetDateTime.now() : null));
                } else {
                    steps.add(step);
                }
            }
            if (!found) throw new IllegalArgumentException("Etapa nao encontrada.");
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), steps,
                    p.cronograma(), p.alocacoes(), p.financeiro(), p.riscos(),
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    // ── Project Allocations ───────────────────────────────────────────────────

    public ProjectRecord addAllocation(String projectId, CreateAllocationRequest request) throws IOException {
        if (request == null || request.nomePessoa() == null || request.nomePessoa().isBlank())
            throw new IllegalArgumentException("Nome da pessoa e obrigatorio.");
        if (request.perfilId() == null || request.perfilId().isBlank())
            throw new IllegalArgumentException("Perfil e obrigatorio.");
        if (request.horasPlanejadas() <= 0)
            throw new IllegalArgumentException("Horas planejadas deve ser maior que zero.");
        Profile profile = profileService.loadProfiles().stream()
                .filter(p -> p.id().equals(request.perfilId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil nao encontrado."));
        BigDecimal valorHoraAplicado = resolveValorHoraPessoa(request.nomePessoa(), profile);
        return update(projectId, p -> {
            List<Allocation> allocations = new ArrayList<>(p.alocacoes());
            BigDecimal custo = valorHoraAplicado.multiply(BigDecimal.valueOf(request.horasPlanejadas()));
            allocations.add(new Allocation(UUID.randomUUID().toString(), request.nomePessoa().trim(),
                    profile.id(), profile.nomePerfil(), valorHoraAplicado, profile.debitaLo(),
                    request.horasPlanejadas(), custo, OffsetDateTime.now()));
            log.info("Alocacao adicionada: projetoId={}, pessoa={}, perfil={}, horas={}",
                    projectId, request.nomePessoa(), profile.nomePerfil(), request.horasPlanejadas());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(),
                    p.cronograma(), allocations, p.financeiro(), p.riscos(),
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    // ── Finance ───────────────────────────────────────────────────────────────

    public ProjectRecord addFinanceEntry(String projectId, CreateFinanceEntryRequest request) throws IOException {
        if (request == null || request.tipo() == null || request.valor() == null)
            throw new IllegalArgumentException("Tipo e valor sao obrigatorios.");
        String tipo = request.tipo().trim().toUpperCase();
        if (!tipo.equals("RECEITA") && !tipo.equals("DESPESA"))
            throw new IllegalArgumentException("Tipo deve ser RECEITA ou DESPESA.");
        return update(projectId, p -> {
            List<FinanceEntry> entries = new ArrayList<>(p.financeiro());
            entries.add(new FinanceEntry(UUID.randomUUID().toString(), tipo,
                    request.descricao() == null ? "" : request.descricao().trim(),
                    request.valor(),
                    request.dataLancamento() == null ? OffsetDateTime.now() : request.dataLancamento(),
                    OffsetDateTime.now()));
            log.info("Lancamento financeiro adicionado: projetoId={}, tipo={}, valor={}", projectId, tipo, request.valor());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(),
                    p.cronograma(), p.alocacoes(), entries, p.riscos(),
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    public ProjectRecord addScheduleItem(String projectId, CreateScheduleItemRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do item do cronograma e obrigatorio.");
        return update(projectId, p -> {
            List<ScheduleItem> cronograma = new ArrayList<>(p.cronograma());
            int nextOrder = cronograma.size();
            cronograma.add(new ScheduleItem(UUID.randomUUID().toString(), request.titulo().trim(),
                    request.descricao() == null ? "" : request.descricao().trim(),
                    request.inicioPlanejado(), request.fimPlanejado(), request.permiteParalelo(),
                    "PLANEJADO", nextOrder, OffsetDateTime.now(), request.cor(),
                    request.responsavel() == null ? null : request.responsavel().trim()));
            log.info("Item de cronograma criado: projetoId={}, titulo={}", projectId, request.titulo());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(),
                    cronograma, p.alocacoes(), p.financeiro(), p.riscos(),
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord updateScheduleItem(String projectId, String scheduleItemId, UpdateScheduleItemRequest request) throws IOException {
        return update(projectId, p -> {
            List<ScheduleItem> cronograma = new ArrayList<>();
            boolean found = false;
            for (ScheduleItem item : p.cronograma()) {
                if (item.id().equals(scheduleItemId)) {
                    found = true;
                    cronograma.add(new ScheduleItem(item.id(),
                            request.titulo() == null || request.titulo().isBlank() ? item.titulo() : request.titulo().trim(),
                            request.descricao() == null ? item.descricao() : request.descricao().trim(),
                            request.inicioPlanejado() == null ? item.inicioPlanejado() : request.inicioPlanejado(),
                            request.fimPlanejado() == null ? item.fimPlanejado() : request.fimPlanejado(),
                            request.permiteParalelo(),
                            request.status() == null || request.status().isBlank() ? item.status() : request.status().trim().toUpperCase(),
                            item.ordem(), item.criadoEm(),
                            request.cor() == null ? item.cor() : request.cor(),
                            request.responsavel() == null ? item.responsavel() : request.responsavel().trim()));
                } else {
                    cronograma.add(item);
                }
            }
            if (!found) throw new IllegalArgumentException("Item de cronograma nao encontrado.");
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(),
                    cronograma, p.alocacoes(), p.financeiro(), p.riscos(),
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord reorderSchedule(String projectId, ReorderScheduleRequest request) throws IOException {
        if (request == null || request.itemIdsOrdered() == null)
            throw new IllegalArgumentException("Lista de ordenacao do cronograma e obrigatoria.");
        return update(projectId, p -> {
            List<ScheduleItem> existing = new ArrayList<>(p.cronograma());
            if (existing.size() != request.itemIdsOrdered().size())
                throw new IllegalArgumentException("A lista enviada nao corresponde ao total de itens.");
            List<ScheduleItem> reordered = new ArrayList<>();
            for (int i = 0; i < request.itemIdsOrdered().size(); i++) {
                String id = request.itemIdsOrdered().get(i);
                ScheduleItem item = existing.stream().filter(s -> s.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Item de cronograma invalido na ordenacao."));
                reordered.add(new ScheduleItem(item.id(), item.titulo(), item.descricao(),
                        item.inicioPlanejado(), item.fimPlanejado(), item.permiteParalelo(),
                        item.status(), i, item.criadoEm(), item.cor(), item.responsavel()));
            }
            log.info("Cronograma reordenado: projetoId={}, totalItens={}", projectId, reordered.size());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(),
                    reordered, p.alocacoes(), p.financeiro(), p.riscos(),
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    // ── Replanning ────────────────────────────────────────────────────────────

    public ProjectRecord addReplanningEvent(String projectId, AddReplanningEventRequest request) throws IOException {
        if (request == null || request.scheduleItemId() == null || request.scheduleItemId().isBlank())
            throw new IllegalArgumentException("ID do item do cronograma e obrigatorio.");
        if (request.tipoAlteracao() == null || request.tipoAlteracao().isBlank())
            throw new IllegalArgumentException("Tipo de alteracao e obrigatorio.");
        return update(projectId, p -> {
            List<ReplanningEvent> historico = new ArrayList<>(safeReplanList(p.historicoReplanejamento()));
            historico.add(new ReplanningEvent(UUID.randomUUID().toString(),
                    request.scheduleItemId(),
                    request.scheduleItemTitulo() == null ? "" : request.scheduleItemTitulo().trim(),
                    request.tipoAlteracao().trim().toUpperCase(),
                    request.inicioAnterior(), request.fimAnterior(), request.inicioNovo(), request.fimNovo(),
                    OffsetDateTime.now()));
            log.info("Replanejamento registrado: projetoId={}, itemId={}, tipo={}", projectId, request.scheduleItemId(), request.tipoAlteracao());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(),
                    p.cronograma(), p.alocacoes(), p.financeiro(), p.riscos(), historico,
                    p.situacao(), p.donoProjeto());
        });
    }

    // ── Project-level Risks ───────────────────────────────────────────────────

    public ProjectRecord addRiskItem(String projectId, CreateRiskItemRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do apontamento de risco e obrigatorio.");
        if (request.dataFim() == null)
            throw new IllegalArgumentException("Data fim do apontamento de risco e obrigatoria.");
        return update(projectId, p -> {
            List<RiskItem> riscos = new ArrayList<>(p.riscos() == null ? List.of() : p.riscos());
            riscos.add(new RiskItem(UUID.randomUUID().toString(), request.titulo().trim(),
                    request.descricao() == null ? "" : request.descricao().trim(),
                    request.dataFim(), "PLANO_ACAO", OffsetDateTime.now()));
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(),
                    p.cronograma(), p.alocacoes(), p.financeiro(), riscos,
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord updateRiskItemStatus(String projectId, String riskId, UpdateRiskItemStatusRequest request) throws IOException {
        if (request == null || request.status() == null || request.status().isBlank())
            throw new IllegalArgumentException("Status do risco e obrigatorio.");
        String nextStatus = request.status().trim().toUpperCase();
        if (!nextStatus.equals("PLANO_ACAO") && !nextStatus.equals("DESENVOLVIMENTO")
                && !nextStatus.equals("VALIDACAO_EXTERNA")
                && !nextStatus.equals("ENTREGA") && !nextStatus.equals("CONCLUIDO"))
            throw new IllegalArgumentException("Status invalido para risco.");
        return update(projectId, p -> {
            List<RiskItem> riscos = new ArrayList<>();
            boolean found = false;
            for (RiskItem r : (p.riscos() == null ? List.<RiskItem>of() : p.riscos())) {
                if (r.id().equals(riskId)) {
                    found = true;
                    riscos.add(new RiskItem(r.id(), r.titulo(), r.descricao(), r.dataFim(), nextStatus, r.criadoEm()));
                } else {
                    riscos.add(r);
                }
            }
            if (!found) throw new IllegalArgumentException("Apontamento de risco nao encontrado.");
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(),
                    p.cronograma(), p.alocacoes(), p.financeiro(), riscos,
                    safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    // ── Activities ────────────────────────────────────────────────────────────

    public List<ProjectActivity> listAllActivities() throws IOException {
        List<ProjectActivity> result = new ArrayList<>();
        for (ProjectRecord p : load()) {
            String pNome = p.nome() == null ? "" : p.nome();
            List<ScheduleItem> cronograma = p.cronograma() == null ? List.<ScheduleItem>of() : p.cronograma();
            for (ScheduleItem item : cronograma) {
                result.add(new ProjectActivity(item.id(), item.titulo(), item.descricao(),
                        p.id(), pNome, item.inicioPlanejado(), item.fimPlanejado(),
                        item.status(), item.responsavel(), item.cor(), item.criadoEm()));
            }
        }
        return result;
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    public List<ProjectOverviewResponse> overview(String username, String role) throws IOException {
        java.util.Map<String, java.util.Map<String, GanttItemMeta>> ganttMetaByProject =
                loadGanttConfigs().stream().collect(java.util.stream.Collectors.toMap(
                        GanttProjectConfig::projectId,
                        c -> c.meta() != null ? c.meta() : java.util.Map.of(),
                        (a, b) -> a));

        List<ProjectOverviewResponse> out = new ArrayList<>();
        for (ProjectRecord p : list(username, role)) {
            int etapasConcluidas = (int) p.etapas().stream().filter(Step::concluido).count();
            int totalEtapas = p.etapas().size();
            int percentualConclusao;
            List<ScheduleItem> cronograma = p.cronograma() != null ? p.cronograma() : List.of();
            if (cronograma.isEmpty()) {
                percentualConclusao = totalEtapas == 0 ? 0
                        : (int) Math.round((etapasConcluidas * 100.0) / totalEtapas);
            } else {
                java.util.Map<String, GanttItemMeta> metaMap =
                        ganttMetaByProject.getOrDefault(p.id(), java.util.Map.of());
                double sum = cronograma.stream()
                        .mapToDouble(item -> effectivePctItem(item, metaMap))
                        .sum();
                percentualConclusao = (int) Math.round(sum / cronograma.size());
            }
            String status = percentualConclusao >= 100 ? "CONCLUIDO"
                    : (percentualConclusao >= 50 ? "EM_ANDAMENTO" : "PLANEJADO");
            BigDecimal custoEquipe = p.alocacoes().stream()
                    .map(Allocation::custoPlanejado).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal receitas = totalByType(p.financeiro(), "RECEITA");
            BigDecimal despesas = totalByType(p.financeiro(), "DESPESA");
            out.add(new ProjectOverviewResponse(p.id(), p.nome(), p.descricao(), status,
                    percentualConclusao, totalEtapas, etapasConcluidas, p.alocacoes().size(),
                    custoEquipe, receitas, despesas, receitas.subtract(despesas),
                    p.situacao() != null ? p.situacao() : "PUBLISHED", p.donoProjeto()));
        }
        return out;
    }

    // ── Gantt Configs ─────────────────────────────────────────────────────────

    public GanttProjectConfig getGanttConfig(String projectId) throws IOException {
        return loadGanttConfigs().stream()
                .filter(c -> projectId.equals(c.projectId()))
                .findFirst()
                .orElse(new GanttProjectConfig(projectId, new ArrayList<>(), new java.util.HashMap<>(), null, new java.util.HashMap<>()));
    }

    public GanttProjectConfig saveGanttConfig(String projectId, SaveGanttConfigRequest request) throws IOException {
        GanttProjectConfig newConfig = new GanttProjectConfig(
                projectId,
                request == null || request.markers() == null ? new ArrayList<>() : request.markers(),
                request == null || request.meta() == null ? new java.util.HashMap<>() : request.meta(),
                request == null ? null : request.rowHeight(),
                request == null || request.rowHeights() == null ? new java.util.HashMap<>() : request.rowHeights());
        List<GanttProjectConfig> all = new ArrayList<>(loadGanttConfigs());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (projectId.equals(all.get(i).projectId())) { all.set(i, newConfig); replaced = true; break; }
        }
        if (!replaced) all.add(newConfig);
        saveGanttConfigs(all);
        return newConfig;
    }

    // ── Project Budgets ───────────────────────────────────────────────────────

    public List<ProjectBudget> listProjectBudgets() throws IOException {
        return loadProjectBudgets();
    }

    public ProjectBudget createProjectBudget(CreateProjectBudgetRequest req, String criadoPor) throws IOException {
        if (req == null || req.nome() == null || req.nome().isBlank())
            throw new IllegalArgumentException("Nome do orcamento e obrigatorio.");
        ProjectBudget created = new ProjectBudget(UUID.randomUUID().toString(), req.nome().trim(),
                req.descricao(),
                req.atividades() != null ? req.atividades() : new ArrayList<>(),
                OffsetDateTime.now(), criadoPor);
        List<ProjectBudget> all = new ArrayList<>(loadProjectBudgets());
        all.add(created);
        saveProjectBudgets(all);
        return created;
    }

    public ProjectBudget updateProjectBudget(String id, CreateProjectBudgetRequest req) throws IOException {
        if (req == null || req.nome() == null || req.nome().isBlank())
            throw new IllegalArgumentException("Nome do orcamento e obrigatorio.");
        List<ProjectBudget> all = new ArrayList<>(loadProjectBudgets());
        for (int i = 0; i < all.size(); i++) {
            ProjectBudget curr = all.get(i);
            if (curr.id().equals(id)) {
                ProjectBudget updated = new ProjectBudget(curr.id(), req.nome().trim(), req.descricao(),
                        req.atividades() != null ? req.atividades() : new ArrayList<>(),
                        curr.criadoEm(), curr.criadoPor());
                all.set(i, updated);
                saveProjectBudgets(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Orcamento nao encontrado.");
    }

    public void deleteProjectBudget(String id) throws IOException {
        List<ProjectBudget> all = new ArrayList<>(loadProjectBudgets());
        boolean removed = all.removeIf(b -> b.id().equals(id));
        if (!removed) throw new IllegalArgumentException("Orcamento nao encontrado.");
        saveProjectBudgets(all);
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    private List<ProjectRecord> load() throws IOException { return projectStorePort.loadProjects(); }
    private void save(List<ProjectRecord> projects) throws IOException { projectStorePort.saveProjects(projects); }

    private List<GanttProjectConfig> loadGanttConfigs() throws IOException {
        return jsonStore.readList(ganttConfigsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveGanttConfigs(List<GanttProjectConfig> configs) throws IOException {
        jsonStore.writeList(ganttConfigsPath, configs);
    }

    private List<ProjectBudget> loadProjectBudgets() throws IOException {
        return jsonStore.readList(projectBudgetsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveProjectBudgets(List<ProjectBudget> budgets) throws IOException {
        jsonStore.writeList(projectBudgetsPath, budgets);
    }

    private ProjectRecord update(String projectId, Updater updater) throws IOException {
        List<ProjectRecord> all = new ArrayList<>(load());
        for (int i = 0; i < all.size(); i++) {
            ProjectRecord p = all.get(i);
            if (p.id().equals(projectId)) {
                ProjectRecord updated = updater.apply(p);
                all.set(i, updated);
                save(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Projeto nao encontrado.");
    }

    // ── Private utility helpers ───────────────────────────────────────────────

    private BigDecimal resolveValorHoraPessoa(String nomePessoa, Profile profile) throws IOException {
        if (!profile.debitaLo()) return BigDecimal.ZERO;
        String nome = normalized(nomePessoa);
        if (nome.isBlank()) return profile.valorHora();
        // load people via personService not available here — use jsonStore directly via peoplePath
        // We reuse profileService which has the peoplePath already exposed through loadProfiles;
        // for people we access them through personService if needed. Since ProjectService doesn't
        // depend on PersonService directly we replicate the logic with the profile valorHora fallback.
        return profile.valorHora();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private BigDecimal totalByType(List<FinanceEntry> entries, String type) {
        return entries.stream().filter(e -> type.equals(e.tipo()))
                .map(FinanceEntry::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private double effectivePctItem(ScheduleItem item, java.util.Map<String, GanttItemMeta> metaMap) {
        String st = String.valueOf(item.status() != null ? item.status() : "").toUpperCase();
        if ("CONCLUIDO".equals(st)) return 100.0;
        List<java.util.Map<String, Object>> etapas = extractEtapasFromDesc(item.descricao());
        if (!etapas.isEmpty()) {
            long done = etapas.stream().filter(e -> Boolean.TRUE.equals(e.get("done"))).count();
            return (done * 100.0) / etapas.size();
        }
        GanttItemMeta meta = metaMap.get(item.id());
        return meta != null ? Math.max(0, Math.min(100, (double) meta.pct())) : 0.0;
    }

    private static final String ET_TAG     = "##ETAPAS:";
    private static final String ET_TAG_END = "##";

    private List<java.util.Map<String, Object>> extractEtapasFromDesc(String descricao) {
        if (descricao == null) return List.of();
        int idx = descricao.indexOf(ET_TAG);
        if (idx < 0) return List.of();
        int start = idx + ET_TAG.length();
        int end = descricao.indexOf(ET_TAG_END, start);
        if (end < 0) return List.of();
        try {
            return jsonStore.getObjectMapper().readValue(
                    descricao.substring(start, end),
                    new com.fasterxml.jackson.core.type.TypeReference<List<java.util.Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("Falha ao parsear etapas embutidas na atividade: {}", e.getMessage());
            return List.of();
        }
    }

    private static boolean matchesValue(String current, String expected) {
        return current != null && !current.isBlank() && expected != null && !expected.isBlank()
                && current.trim().equalsIgnoreCase(expected.trim());
    }

    private static List<ReplanningEvent> safeReplanList(List<ReplanningEvent> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static List<IncidentHistoryEvent> safeIncidentHistory(List<IncidentHistoryEvent> h) {
        return h == null ? new ArrayList<>() : new ArrayList<>(h);
    }

    private static List<TechnicalDebtHistoryEvent> safeDebtHistory(List<TechnicalDebtHistoryEvent> h) {
        return h == null ? new ArrayList<>() : new ArrayList<>(h);
    }

    private void ensureDataFiles() {
        try {
            ensureFile(ganttConfigsPath);
            ensureFile(projectBudgetsPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de dados.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface Updater {
        ProjectRecord apply(ProjectRecord project);
    }
}
