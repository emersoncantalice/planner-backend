package com.planner.backend.project;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.application.port.ProjectStorePort;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ProjectService {
    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private final ProjectStorePort projectStorePort;
    private final java.nio.file.Path profilesPath;
    private final java.nio.file.Path budgetLinesPath;
    private final java.nio.file.Path businessEpicsPath;
    private final java.nio.file.Path budgetAllocationsPath;
    private final java.nio.file.Path budgetLineAdjustmentsPath;
    private final java.nio.file.Path globalRisksPath;
    private final java.nio.file.Path peoplePath;
    private final java.nio.file.Path monthlyHoursPath;
    private final java.nio.file.Path consultanciesPath;
    private final java.nio.file.Path focalPointsPath;
    private final java.nio.file.Path absencesPath;
    private final java.nio.file.Path incidentsPath;
    private final java.nio.file.Path technicalDebtsPath;
    private final java.nio.file.Path indicatorsPath;
    private final java.nio.file.Path allocationPaymentsPath;
    private final java.nio.file.Path loPresencePath;
    private final java.nio.file.Path allocationMonthlyStatePath;
    private final java.nio.file.Path allocationCursorsPath;
    private final java.nio.file.Path feriadosPath;
    private final java.nio.file.Path ganttConfigsPath;
    private final com.planner.backend.auth.FileJsonStore jsonStore;

    public ProjectService(
            ProjectStorePort projectStorePort,
            com.planner.backend.auth.FileJsonStore jsonStore,
            @org.springframework.beans.factory.annotation.Value("${planner.data-dir:data}") String dataDir) {
        this.projectStorePort = projectStorePort;
        this.jsonStore = jsonStore;
        this.profilesPath = java.nio.file.Path.of(dataDir, "profiles.json");
        this.budgetLinesPath = java.nio.file.Path.of(dataDir, "budget-lines.json");
        this.businessEpicsPath = java.nio.file.Path.of(dataDir, "business-epics.json");
        this.budgetAllocationsPath = java.nio.file.Path.of(dataDir, "budget-allocations.json");
        this.budgetLineAdjustmentsPath = java.nio.file.Path.of(dataDir, "budget-line-adjustments.json");
        this.globalRisksPath = java.nio.file.Path.of(dataDir, "risks.json");
        this.peoplePath = java.nio.file.Path.of(dataDir, "people.json");
        this.monthlyHoursPath = java.nio.file.Path.of(dataDir, "monthly-hours.json");
        this.consultanciesPath = java.nio.file.Path.of(dataDir, "consultancies.json");
        this.focalPointsPath = java.nio.file.Path.of(dataDir, "focal-points.json");
        this.absencesPath       = java.nio.file.Path.of(dataDir, "absences.json");
        this.incidentsPath      = java.nio.file.Path.of(dataDir, "incidents.json");
        this.technicalDebtsPath = java.nio.file.Path.of(dataDir, "technical-debts.json");
        this.indicatorsPath     = java.nio.file.Path.of(dataDir, "indicators.json");
        this.allocationPaymentsPath = java.nio.file.Path.of(dataDir, "allocation-payments.json");
        this.loPresencePath = java.nio.file.Path.of(dataDir, "lo-presence.json");
        this.allocationMonthlyStatePath = java.nio.file.Path.of(dataDir, "allocation-monthly-state.json");
        this.allocationCursorsPath = java.nio.file.Path.of(dataDir, "allocation-cursors.json");
        this.feriadosPath = java.nio.file.Path.of(dataDir, "feriados.json");
        this.ganttConfigsPath = java.nio.file.Path.of(dataDir, "gantt-configs.json");
        ensureDataFiles();
    }

    /**
     * Returns projects visible to the given user.
     *  - ADMIN: all published + own drafts + ownerless drafts (legacy)
     *  - Regular user: own projects (any status) + other users' published projects + ownerless
     */
    public List<ProjectRecord> list(String username, String role) throws IOException {
        List<ProjectRecord> all = load();
        if ("ADMIN".equals(role)) {
            return all.stream()
                    .filter(p -> !"DRAFT".equals(p.situacao())
                            || p.donoProjeto() == null
                            || username.equalsIgnoreCase(p.donoProjeto()))
                    .toList();
        }
        // Regular user: own projects (any status) OR ownerless OR published by anyone
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
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do projeto e obrigatorio.");
        }
        ProjectRecord project = new ProjectRecord(
                UUID.randomUUID().toString(),
                request.nome().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                OffsetDateTime.now(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                "DRAFT",
                donoProjeto);
        List<ProjectRecord> all = new ArrayList<>(load());
        all.add(project);
        save(all);
        log.info("Projeto criado: id={}, nome={}, dono={}", project.id(), project.nome(), donoProjeto);
        return project;
    }

    public ReplaceOwnershipResponse replaceOwnershipReferences(String oldValue, String newValue) throws IOException {
        if (oldValue == null || oldValue.isBlank()) {
            throw new IllegalArgumentException("Valor atual e obrigatorio.");
        }
        if (newValue == null || newValue.isBlank()) {
            throw new IllegalArgumentException("Novo valor e obrigatorio.");
        }
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

        List<ProjectRecord> projetos = new ArrayList<>(load());
        for (int i = 0; i < projetos.size(); i++) {
            ProjectRecord p = projetos.get(i);
            boolean changed = false;
            String donoProjeto = p.donoProjeto();
            if (matchesValue(donoProjeto, oldNorm)) {
                donoProjeto = newNorm;
                projetosDonoAtualizados++;
                changed = true;
            }
            List<ScheduleItem> cronograma = new ArrayList<>();
            for (ScheduleItem item : p.cronograma()) {
                String responsavel = item.responsavel();
                if (matchesValue(responsavel, oldNorm)) {
                    responsavel = newNorm;
                    cronogramasResponsavelAtualizados++;
                    changed = true;
                }
                cronograma.add(new ScheduleItem(
                        item.id(), item.titulo(), item.descricao(),
                        item.inicioPlanejado(), item.fimPlanejado(), item.permiteParalelo(),
                        item.status(), item.ordem(), item.criadoEm(), item.cor(), responsavel));
            }
            if (changed) {
                projetos.set(i, new ProjectRecord(
                        p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), cronograma,
                        p.alocacoes(), p.financeiro(), p.riscos(), safeReplanList(p.historicoReplanejamento()),
                        p.situacao(), donoProjeto));
            }
        }
        save(projetos);

        List<BudgetLine> los = new ArrayList<>(loadBudgetLines());
        for (int i = 0; i < los.size(); i++) {
            BudgetLine lo = los.get(i);
            if (matchesValue(lo.dono(), oldNorm)) {
                los.set(i, new BudgetLine(
                        lo.id(), lo.codigo(), lo.nome(), lo.ano(), lo.tipo(), lo.centroCusto(),
                        lo.valorTotal(), lo.criadoEm(), lo.situacao(), newNorm));
                linhasOrcamentariasDonoAtualizadas++;
            }
        }
        saveBudgetLines(los);

        List<GlobalRisk> riscos = new ArrayList<>(loadGlobalRisks());
        for (int i = 0; i < riscos.size(); i++) {
            GlobalRisk r = riscos.get(i);
            if (matchesValue(r.responsavel(), oldNorm)) {
                riscos.set(i, new GlobalRisk(
                        r.id(), r.titulo(), r.descricao(), r.planoAcao(), r.dataFim(), r.status(),
                        newNorm, r.historico(), r.criadoEm()));
                riscosResponsavelAtualizados++;
            }
        }
        saveGlobalRisks(riscos);

        List<Incident> incidents = new ArrayList<>(loadIncidents());
        for (int i = 0; i < incidents.size(); i++) {
            Incident it = incidents.get(i);
            if (matchesValue(it.responsavel(), oldNorm)) {
                incidents.set(i, new Incident(
                        it.id(), it.titulo(), it.descricao(), it.tipo(), it.severidade(), it.status(),
                        newNorm, it.dataOcorrencia(), it.dataResolucao(), it.impacto(), it.causaRaiz(),
                        it.acoesCorrativas(), safeIncidentHistory(it.historico()), it.criadoEm()));
                incidentesResponsavelAtualizados++;
            }
        }
        saveIncidents(incidents);

        List<TechnicalDebt> debts = new ArrayList<>(loadTechnicalDebts());
        for (int i = 0; i < debts.size(); i++) {
            TechnicalDebt d = debts.get(i);
            if (matchesValue(d.responsavel(), oldNorm)) {
                debts.set(i, new TechnicalDebt(
                        d.id(), d.titulo(), d.descricao(), d.categoria(), d.impacto(), d.esforcoEstimado(),
                        d.prioridade(), d.status(), newNorm, d.projetoRef(), d.dataAlvo(), d.resolvidoEm(),
                        safeDebtHistory(d.historico()), d.criadoEm()));
                debitosResponsavelAtualizados++;
            }
        }
        saveTechnicalDebts(debts);

        List<Indicator> indicators = new ArrayList<>(loadIndicators());
        for (int i = 0; i < indicators.size(); i++) {
            Indicator ind = indicators.get(i);
            boolean changed = false;
            String responsavel = ind.responsavel();
            if (matchesValue(responsavel, oldNorm)) {
                responsavel = newNorm;
                indicadoresResponsavelAtualizados++;
                changed = true;
            }
            List<IndicatorAction> acoes = new ArrayList<>();
            for (IndicatorAction a : safeIndList(ind.acoes())) {
                String respAcao = a.responsavel();
                if (matchesValue(respAcao, oldNorm)) {
                    respAcao = newNorm;
                    acoesIndicadorResponsavelAtualizadas++;
                    changed = true;
                }
                acoes.add(new IndicatorAction(
                        a.id(), a.descricao(), respAcao, a.status(), a.cicloAberto(), a.cicloConcluido(),
                        a.prazo(), a.concluidoEm(), a.criadoEm()));
            }
            if (changed) {
                indicators.set(i, new Indicator(
                        ind.id(), ind.titulo(), ind.descricao(), ind.tipo(), ind.categoria(), ind.unidade(),
                        ind.meta(), ind.polaridade(), ind.frequencia(), responsavel, ind.status(),
                        safeIndList(ind.ciclos()), acoes, ind.criadoEm()));
            }
        }
        saveIndicators(indicators);

        int total = projetosDonoAtualizados + cronogramasResponsavelAtualizados + linhasOrcamentariasDonoAtualizadas
                + riscosResponsavelAtualizados + incidentesResponsavelAtualizados + debitosResponsavelAtualizados
                + indicadoresResponsavelAtualizados + acoesIndicadorResponsavelAtualizadas;
        return new ReplaceOwnershipResponse(
                projetosDonoAtualizados,
                cronogramasResponsavelAtualizados,
                linhasOrcamentariasDonoAtualizadas,
                riscosResponsavelAtualizados,
                incidentesResponsavelAtualizados,
                debitosResponsavelAtualizados,
                indicadoresResponsavelAtualizados,
                acoesIndicadorResponsavelAtualizadas,
                total);
    }

    private static boolean matchesValue(String current, String expected) {
        return current != null && !current.isBlank() && expected != null && !expected.isBlank()
                && current.trim().equalsIgnoreCase(expected.trim());
    }

    // Legacy overload (for internal/template use)
    public ProjectRecord create(CreateProjectRequest request) throws IOException {
        return create(request, null);
    }

    public ProjectRecord updateSituacao(String projectId, String situacao, String username, String role) throws IOException {
        if (!"DRAFT".equals(situacao) && !"PUBLISHED".equals(situacao))
            throw new IllegalArgumentException("Situação inválida. Use DRAFT ou PUBLISHED.");
        return update(projectId, p -> {
            boolean isOwner = username.equalsIgnoreCase(p.donoProjeto()) || p.donoProjeto() == null;
            if (!isOwner && !"ADMIN".equals(role))
                throw new IllegalArgumentException("Sem permissão para alterar a situação deste projeto.");
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(),
                    p.etapas(), p.cronograma(), p.alocacoes(), p.financeiro(),
                    p.riscos(), safeReplanList(p.historicoReplanejamento()),
                    situacao, p.donoProjeto());
        });
    }

    public ProjectRecord updateProject(String projectId, CreateProjectRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do projeto e obrigatorio.");
        }
        return update(projectId, p -> new ProjectRecord(
                p.id(),
                request.nome().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                p.criadoEm(),
                p.etapas(),
                p.cronograma(),
                p.alocacoes(),
                p.financeiro(),
                p.riscos(),
                safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto()));
    }

    public void deleteProject(String projectId) throws IOException {
        List<ProjectRecord> all = new ArrayList<>(load());
        boolean removed = all.removeIf(p -> p.id().equals(projectId));
        if (!removed) throw new IllegalArgumentException("Projeto nao encontrado.");
        save(all);
    }

    public ProjectRecord addStep(String projectId, CreateStepRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank()) {
            throw new IllegalArgumentException("Titulo da etapa e obrigatorio.");
        }
        return update(projectId, p -> {
            List<Step> steps = new ArrayList<>(p.etapas());
            steps.add(new Step(UUID.randomUUID().toString(), request.titulo().trim(), false, null));
            log.info("Etapa adicionada: projetoId={}, titulo={}", projectId, request.titulo());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), steps, p.cronograma(), p.alocacoes(), p.financeiro(), p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord toggleStep(String projectId, String stepId, ToggleStepRequest request) throws IOException {
        return update(projectId, p -> {
            List<Step> steps = new ArrayList<>();
            boolean found = false;
            for (Step step : p.etapas()) {
                if (step.id().equals(stepId)) {
                    found = true;
                    steps.add(new Step(step.id(), step.titulo(), request.concluido(), request.concluido() ? OffsetDateTime.now() : null));
                } else {
                    steps.add(step);
                }
            }
            if (!found) throw new IllegalArgumentException("Etapa nao encontrada.");
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), steps, p.cronograma(), p.alocacoes(), p.financeiro(), p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord addAllocation(String projectId, CreateAllocationRequest request) throws IOException {
        if (request == null || request.nomePessoa() == null || request.nomePessoa().isBlank()) {
            throw new IllegalArgumentException("Nome da pessoa e obrigatorio.");
        }
        if (request.perfilId() == null || request.perfilId().isBlank()) {
            throw new IllegalArgumentException("Perfil e obrigatorio.");
        }
        if (request.horasPlanejadas() <= 0) {
            throw new IllegalArgumentException("Horas planejadas deve ser maior que zero.");
        }
        Profile profile = loadProfiles().stream()
                .filter(p -> p.id().equals(request.perfilId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil nao encontrado."));
        BigDecimal valorHoraAplicado = resolveValorHoraPessoa(request.nomePessoa(), profile);

        return update(projectId, p -> {
            List<Allocation> allocations = new ArrayList<>(p.alocacoes());
            BigDecimal custo = valorHoraAplicado.multiply(BigDecimal.valueOf(request.horasPlanejadas()));
            allocations.add(new Allocation(
                    UUID.randomUUID().toString(),
                    request.nomePessoa().trim(),
                    profile.id(),
                    profile.nomePerfil(),
                    valorHoraAplicado,
                    profile.debitaLo(),
                    request.horasPlanejadas(),
                    custo,
                    OffsetDateTime.now()));
            log.info("Alocacao adicionada: projetoId={}, pessoa={}, perfil={}, horas={}", projectId, request.nomePessoa(), profile.nomePerfil(), request.horasPlanejadas());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), p.cronograma(), allocations, p.financeiro(), p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

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
        boolean removed = all.removeIf(p -> p.id().equals(profileId));
        if (!removed) throw new IllegalArgumentException("Perfil nao encontrado.");
        saveProfiles(all);
    }

    /**
     * Returns budget lines visible to the given user.
     *  - ADMIN: all published + own drafts + ownerless
     *  - Regular user: own LOs (any status) + other users' published LOs + ownerless
     */
    public List<BudgetLine> listBudgetLines(String username, String role) throws IOException {
        List<BudgetLine> all = loadBudgetLines();
        if ("ADMIN".equals(role)) {
            return all.stream()
                    .filter(lo -> !"DRAFT".equals(lo.situacao())
                            || lo.dono() == null
                            || username.equalsIgnoreCase(lo.dono()))
                    .toList();
        }
        // Regular user: own LOs (any status) OR ownerless OR published by anyone
        return all.stream()
                .filter(lo -> lo.dono() == null
                        || username.equalsIgnoreCase(lo.dono())
                        || !"DRAFT".equals(lo.situacao()))
                .toList();
    }

    public List<BudgetLine> listBudgetLines() throws IOException { return loadBudgetLines(); }

    public BudgetLine updateBudgetLineSituacao(String id, String situacao, String username, String role) throws IOException {
        if (!"DRAFT".equals(situacao) && !"PUBLISHED".equals(situacao))
            throw new IllegalArgumentException("Situação inválida. Use DRAFT ou PUBLISHED.");
        List<BudgetLine> all = new ArrayList<>(loadBudgetLines());
        for (int i = 0; i < all.size(); i++) {
            BudgetLine lo = all.get(i);
            if (lo.id().equals(id)) {
                boolean isOwner = username.equalsIgnoreCase(lo.dono()) || lo.dono() == null;
                if (!isOwner && !"ADMIN".equals(role))
                    throw new IllegalArgumentException("Sem permissão para alterar a situação desta LO.");
                BudgetLine updated = new BudgetLine(lo.id(), lo.codigo(), lo.nome(), lo.ano(),
                        lo.tipo(), lo.centroCusto(), lo.valorTotal(), lo.criadoEm(), situacao, lo.dono());
                all.set(i, updated);
                saveBudgetLines(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("LO não encontrada.");
    }

    public BudgetLine createBudgetLine(CreateBudgetLineRequest request, String dono) throws IOException {
        if (request == null || request.codigo() == null || request.codigo().isBlank())
            throw new IllegalArgumentException("Codigo da LO e obrigatorio.");
        if (request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome da LO e obrigatorio.");
        if (request.valorTotal() == null || request.valorTotal().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Valor total da LO deve ser maior que zero.");
        int ano = sanitizeAno(request.ano());
        List<BudgetLine> all = new ArrayList<>(loadBudgetLines());
        boolean codigoDuplicado = all.stream().anyMatch(lo -> lo.codigo().equalsIgnoreCase(request.codigo().trim()));
        if (codigoDuplicado) throw new IllegalArgumentException("Ja existe uma LO com este codigo.");
        BudgetLine created = new BudgetLine(
                UUID.randomUUID().toString(),
                request.codigo().trim(),
                request.nome().trim(),
                ano,
                request.tipo() == null || request.tipo().isBlank() ? "RUN" : request.tipo().trim().toUpperCase(),
                request.centroCusto() == null ? "" : request.centroCusto().trim(),
                request.valorTotal(),
                OffsetDateTime.now(),
                "DRAFT",
                dono);
        all.add(created);
        saveBudgetLines(all);
        log.info("LO criada: id={}, codigo={}, nome={}, dono={}", created.id(), created.codigo(), created.nome(), dono);
        return created;
    }

    public BudgetLine createBudgetLine(CreateBudgetLineRequest request) throws IOException {
        return createBudgetLine(request, null);
    }

    public ImportCsvResponse importBudgetLinesCsv(ImportBudgetLinesCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("codigo")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 6) {
                ignorados++;
                continue;
            }
            try {
                createBudgetLine(new CreateBudgetLineRequest(
                        stripQuotes(cols[0]),
                        stripQuotes(cols[1]),
                        parseInteger(cols[2]),
                        stripQuotes(cols[3]),
                        stripQuotes(cols[4]),
                        parseDecimal(cols[5])));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public BudgetLine updateBudgetLine(String budgetLineId, CreateBudgetLineRequest request) throws IOException {
        if (request == null || request.codigo() == null || request.codigo().isBlank()) {
            throw new IllegalArgumentException("Codigo da LO e obrigatorio.");
        }
        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome da LO e obrigatorio.");
        }
        if (request.valorTotal() == null || request.valorTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor total da LO deve ser maior que zero.");
        }
        int ano = sanitizeAno(request.ano());
        List<BudgetLine> all = new ArrayList<>(loadBudgetLines());
        for (int i = 0; i < all.size(); i++) {
            BudgetLine lo = all.get(i);
            if (lo.id().equals(budgetLineId)) {
                BudgetLine updated = new BudgetLine(
                        lo.id(),
                        request.codigo().trim(),
                        request.nome().trim(),
                        ano,
                        request.tipo() == null || request.tipo().isBlank() ? "RUN" : request.tipo().trim().toUpperCase(),
                        request.centroCusto() == null ? "" : request.centroCusto().trim(),
                        request.valorTotal(),
                        lo.criadoEm(),
                        lo.situacao(),
                        lo.dono());
                all.set(i, updated);
                saveBudgetLines(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("LO nao encontrada.");
    }

    public void deleteBudgetLine(String budgetLineId) throws IOException {
        List<BudgetLine> all = new ArrayList<>(loadBudgetLines());
        boolean removed = all.removeIf(lo -> lo.id().equals(budgetLineId));
        if (!removed) throw new IllegalArgumentException("LO nao encontrada.");
        saveBudgetLines(all);

        // Cascade: remove todas as alocações vinculadas a esta LO
        List<BudgetAllocation> allocations = new ArrayList<>(loadBudgetAllocations());
        allocations.removeIf(a -> budgetLineId.equals(a.linhaOrcamentariaId()));
        saveBudgetAllocations(allocations);

        // Cascade: remove todos os ajustes vinculados a esta LO
        List<BudgetLineAdjustment> adjustments = new ArrayList<>(loadBudgetLineAdjustments());
        adjustments.removeIf(a -> budgetLineId.equals(a.budgetLineId()));
        saveBudgetLineAdjustments(adjustments);
    }

    public List<BudgetLineAdjustment> listBudgetLineAdjustments() throws IOException {
        return loadBudgetLineAdjustments();
    }

    public BudgetLineAdjustment createBudgetLineAdjustment(CreateBudgetLineAdjustmentRequest request) throws IOException {
        validateBudgetLineAdjustmentRequest(request);
        BudgetLine lo = loadBudgetLines().stream()
                .filter(b -> b.id().equals(request.budgetLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("LO nao encontrada para registrar movimentacao."));
        BudgetLineAdjustment created = new BudgetLineAdjustment(
                UUID.randomUUID().toString(),
                lo.id(),
                request.tipo().trim().toUpperCase(),
                request.descricao() == null ? "" : request.descricao().trim(),
                request.valor(),
                OffsetDateTime.now());
        List<BudgetLineAdjustment> all = new ArrayList<>(loadBudgetLineAdjustments());
        all.add(created);
        saveBudgetLineAdjustments(all);
        return created;
    }

    public BudgetLineAdjustment updateBudgetLineAdjustment(String adjustmentId, CreateBudgetLineAdjustmentRequest request) throws IOException {
        validateBudgetLineAdjustmentRequest(request);
        BudgetLine lo = loadBudgetLines().stream()
                .filter(b -> b.id().equals(request.budgetLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("LO nao encontrada para atualizar movimentacao."));
        List<BudgetLineAdjustment> all = new ArrayList<>(loadBudgetLineAdjustments());
        for (int i = 0; i < all.size(); i++) {
            BudgetLineAdjustment current = all.get(i);
            if (current.id().equals(adjustmentId)) {
                BudgetLineAdjustment updated = new BudgetLineAdjustment(
                        current.id(),
                        lo.id(),
                        request.tipo().trim().toUpperCase(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        request.valor(),
                        current.criadoEm());
                all.set(i, updated);
                saveBudgetLineAdjustments(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Movimentacao da LO nao encontrada.");
    }

    public void deleteBudgetLineAdjustment(String adjustmentId) throws IOException {
        List<BudgetLineAdjustment> all = new ArrayList<>(loadBudgetLineAdjustments());
        boolean removed = all.removeIf(a -> a.id().equals(adjustmentId));
        if (!removed) throw new IllegalArgumentException("Movimentacao da LO nao encontrada.");
        saveBudgetLineAdjustments(all);
    }

    public List<BusinessEpic> listBusinessEpics() throws IOException {
        return loadBusinessEpics();
    }

    public BusinessEpic createBusinessEpic(CreateBusinessEpicRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do Business Epic e obrigatorio.");
        }
        if (request.jiraUrl() == null || request.jiraUrl().isBlank()) {
            throw new IllegalArgumentException("Link Jira do Business Epic e obrigatorio.");
        }
        if (request.inicio() == null || request.fim() == null) {
            throw new IllegalArgumentException("Datas de inicio e fim do Business Epic sao obrigatorias.");
        }
        if (request.fim().isBefore(request.inicio())) {
            throw new IllegalArgumentException("Data fim do Business Epic nao pode ser anterior ao inicio.");
        }

        BusinessEpic created = new BusinessEpic(
                UUID.randomUUID().toString(),
                request.nome().trim(),
                request.aliasLink() == null ? "" : request.aliasLink().trim(),
                request.jiraUrl().trim(),
                request.inicio(),
                request.fim(),
                OffsetDateTime.now());
        List<BusinessEpic> all = new ArrayList<>(loadBusinessEpics());
        all.add(created);
        saveBusinessEpics(all);
        log.info("Business Epic criado: id={}, nome={}", created.id(), created.nome());
        return created;
    }

    public ImportCsvResponse importBusinessEpicsCsv(ImportBusinessEpicsCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("jira")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 4) {
                ignorados++;
                continue;
            }
            try {
                String nome = stripQuotes(cols[0]);
                String alias = cols.length >= 5 ? stripQuotes(cols[1]) : "";
                String jira = cols.length >= 5 ? stripQuotes(cols[2]) : stripQuotes(cols[1]);
                String inicio = cols.length >= 5 ? cols[3] : cols[2];
                String fim = cols.length >= 5 ? cols[4] : cols[3];
                createBusinessEpic(new CreateBusinessEpicRequest(
                        nome,
                        alias,
                        jira,
                        parseDate(inicio),
                        parseDate(fim)));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public BusinessEpic updateBusinessEpic(String epicId, CreateBusinessEpicRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do Business Epic e obrigatorio.");
        }
        if (request.jiraUrl() == null || request.jiraUrl().isBlank()) {
            throw new IllegalArgumentException("Link Jira do Business Epic e obrigatorio.");
        }
        if (request.inicio() == null || request.fim() == null) {
            throw new IllegalArgumentException("Datas de inicio e fim do Business Epic sao obrigatorias.");
        }
        if (request.fim().isBefore(request.inicio())) {
            throw new IllegalArgumentException("Data fim do Business Epic nao pode ser anterior ao inicio.");
        }
        List<BusinessEpic> all = new ArrayList<>(loadBusinessEpics());
        for (int i = 0; i < all.size(); i++) {
            BusinessEpic e = all.get(i);
            if (e.id().equals(epicId)) {
                BusinessEpic updated = new BusinessEpic(
                        e.id(),
                        request.nome().trim(),
                        request.aliasLink() == null ? "" : request.aliasLink().trim(),
                        request.jiraUrl().trim(),
                        request.inicio(),
                        request.fim(),
                        e.criadoEm());
                all.set(i, updated);
                saveBusinessEpics(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Business Epic nao encontrado.");
    }

    public void deleteBusinessEpic(String epicId) throws IOException {
        List<BusinessEpic> all = new ArrayList<>(loadBusinessEpics());
        boolean removed = all.removeIf(e -> e.id().equals(epicId));
        if (!removed) throw new IllegalArgumentException("Business Epic nao encontrado.");
        saveBusinessEpics(all);
    }

    public List<BudgetAllocation> listBudgetAllocations() throws IOException {
        return loadBudgetAllocations();
    }

    public BudgetAllocation createBudgetAllocation(CreateBudgetAllocationRequest request) throws IOException {
        if (request == null || request.linhaOrcamentariaId() == null || request.linhaOrcamentariaId().isBlank()) {
            throw new IllegalArgumentException("LO da alocacao e obrigatoria.");
        }
        if (request.nomePessoa() == null || request.nomePessoa().isBlank()) {
            throw new IllegalArgumentException("Nome da pessoa e obrigatorio.");
        }
        if (request.perfilId() == null || request.perfilId().isBlank()) {
            throw new IllegalArgumentException("Perfil e obrigatorio.");
        }
        if (request.horasPlanejadas() <= 0) {
            throw new IllegalArgumentException("Horas planejadas deve ser maior que zero.");
        }

        Profile profile = loadProfiles().stream()
                .filter(p -> p.id().equals(request.perfilId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil nao encontrado."));
        BudgetLine lo = loadBudgetLines().stream()
                .filter(b -> b.id().equals(request.linhaOrcamentariaId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("LO nao encontrada."));

        BigDecimal valorHoraAplicado = resolveValorHoraPessoa(request.nomePessoa(), profile);
        BigDecimal custo = valorHoraAplicado.multiply(BigDecimal.valueOf(request.horasPlanejadas()));
        BudgetAllocation created = new BudgetAllocation(
                UUID.randomUUID().toString(),
                lo.id(),
                lo.codigo(),
                request.nomePessoa().trim(),
                profile.id(),
                profile.nomePerfil(),
                valorHoraAplicado,
                profile.debitaLo(),
                request.horasPlanejadas(),
                custo,
                OffsetDateTime.now());
        List<BudgetAllocation> all = new ArrayList<>(loadBudgetAllocations());
        all.add(created);
        saveBudgetAllocations(all);
        return created;
    }

    public BudgetAllocation updateBudgetAllocation(String allocationId, CreateBudgetAllocationRequest request) throws IOException {
        if (request == null || request.linhaOrcamentariaId() == null || request.linhaOrcamentariaId().isBlank()) {
            throw new IllegalArgumentException("LO da alocacao e obrigatoria.");
        }
        if (request.nomePessoa() == null || request.nomePessoa().isBlank()) {
            throw new IllegalArgumentException("Nome da pessoa e obrigatorio.");
        }
        if (request.perfilId() == null || request.perfilId().isBlank()) {
            throw new IllegalArgumentException("Perfil e obrigatorio.");
        }
        if (request.horasPlanejadas() <= 0) {
            throw new IllegalArgumentException("Horas planejadas deve ser maior que zero.");
        }
        Profile profile = loadProfiles().stream()
                .filter(p -> p.id().equals(request.perfilId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil nao encontrado."));
        BudgetLine lo = loadBudgetLines().stream()
                .filter(b -> b.id().equals(request.linhaOrcamentariaId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("LO nao encontrada."));

        List<BudgetAllocation> all = new ArrayList<>(loadBudgetAllocations());
        for (int i = 0; i < all.size(); i++) {
            BudgetAllocation a = all.get(i);
            if (a.id().equals(allocationId)) {
                BigDecimal valorHoraAplicado = resolveValorHoraPessoa(request.nomePessoa(), profile);
                BigDecimal custo = valorHoraAplicado.multiply(BigDecimal.valueOf(request.horasPlanejadas()));
                BudgetAllocation updated = new BudgetAllocation(
                        a.id(), lo.id(), lo.codigo(), request.nomePessoa().trim(), profile.id(), profile.nomePerfil(),
                        valorHoraAplicado, profile.debitaLo(), request.horasPlanejadas(), custo, a.criadoEm());
                all.set(i, updated);
                saveBudgetAllocations(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Alocacao LO nao encontrada.");
    }

    public void deleteBudgetAllocation(String allocationId) throws IOException {
        List<BudgetAllocation> all = new ArrayList<>(loadBudgetAllocations());
        boolean removed = all.removeIf(a -> a.id().equals(allocationId));
        if (!removed) throw new IllegalArgumentException("Alocacao LO nao encontrada.");
        saveBudgetAllocations(all);
    }

    public List<GlobalRisk> listGlobalRisks() throws IOException {
        List<GlobalRisk> all = loadGlobalRisks();
        List<GlobalRisk> normalized = new ArrayList<>(all.size());
        for (GlobalRisk risk : all) {
            normalized.add(normalizeGlobalRisk(risk));
        }
        return normalized;
    }

    public GlobalRisk createGlobalRisk(CreateGlobalRiskRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank()) {
            throw new IllegalArgumentException("Titulo do apontamento de risco e obrigatorio.");
        }
        if (request.dataFim() == null) {
            throw new IllegalArgumentException("Data fim do apontamento de risco e obrigatoria.");
        }
        String status = request.status() == null || request.status().isBlank()
                ? "PLANO_ACAO"
                : request.status().trim().toUpperCase();
        validateRiskStatus(status);
        GlobalRisk created = new GlobalRisk(
                UUID.randomUUID().toString(),
                request.titulo().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                request.planoAcao() == null ? "" : request.planoAcao().trim(),
                request.dataFim(),
                status,
                request.responsavel() == null ? null : request.responsavel().trim(),
                new ArrayList<>(),
                OffsetDateTime.now());
        List<GlobalRiskHistoryEvent> historico = new ArrayList<>(safeHistory(created.historico()));
        historico.add(historyEvent(
                "CRIACAO",
                "Apontamento criado.",
                null,
                status,
                null,
                request.dataFim(),
                null));
        created = new GlobalRisk(
                created.id(), created.titulo(), created.descricao(), created.planoAcao(),
                created.dataFim(), created.status(), created.responsavel(), historico, created.criadoEm());
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        all.add(created);
        saveGlobalRisks(all);
        return created;
    }

    public GlobalRisk updateGlobalRiskStatus(String riskId, UpdateGlobalRiskStatusRequest request) throws IOException {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException("Status do risco e obrigatorio.");
        }
        String nextStatus = request.status().trim().toUpperCase();
        validateRiskStatus(nextStatus);
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        for (int i = 0; i < all.size(); i++) {
            GlobalRisk r = all.get(i);
            if (r.id().equals(riskId)) {
                List<GlobalRiskHistoryEvent> historico = new ArrayList<>(safeHistory(r.historico()));
                historico.add(historyEvent(
                        "STATUS",
                        "Status alterado.",
                        r.status(),
                        nextStatus,
                        r.dataFim(),
                        r.dataFim(),
                        null));
                GlobalRisk updated = new GlobalRisk(
                        r.id(), r.titulo(), r.descricao(), r.planoAcao(), r.dataFim(), nextStatus, r.responsavel(), historico, r.criadoEm());
                all.set(i, updated);
                saveGlobalRisks(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Apontamento de risco nao encontrado.");
    }

    public GlobalRisk updateGlobalRisk(String riskId, CreateGlobalRiskRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank()) {
            throw new IllegalArgumentException("Titulo do apontamento de risco e obrigatorio.");
        }
        if (request.dataFim() == null) {
            throw new IllegalArgumentException("Data fim do apontamento de risco e obrigatoria.");
        }
        String status = request.status() == null || request.status().isBlank()
                ? "PLANO_ACAO"
                : request.status().trim().toUpperCase();
        validateRiskStatus(status);
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        for (int i = 0; i < all.size(); i++) {
            GlobalRisk r = all.get(i);
            if (r.id().equals(riskId)) {
                List<GlobalRiskHistoryEvent> historico = new ArrayList<>(safeHistory(r.historico()));
                boolean dataAlterada = !sameDateTime(r.dataFim(), request.dataFim());
                if (dataAlterada) {
                    historico.add(historyEvent(
                            "AJUSTE_DATA",
                            "Data de vencimento ajustada na edicao.",
                            r.status(),
                            status,
                            r.dataFim(),
                            request.dataFim(),
                            null));
                }
                GlobalRisk updated = new GlobalRisk(
                        r.id(),
                        request.titulo().trim(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        request.planoAcao() == null ? "" : request.planoAcao().trim(),
                        request.dataFim(),
                        status,
                        request.responsavel() == null ? r.responsavel() : request.responsavel().trim(),
                        historico,
                        r.criadoEm());
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

    public List<ProjectActivity> listAllActivities() throws IOException {
        List<ProjectActivity> result = new ArrayList<>();
        for (ProjectRecord p : load()) {
            String pNome = p.nome() == null ? "" : p.nome();
            List<ScheduleItem> cronograma = p.cronograma() == null ? List.<ScheduleItem>of() : p.cronograma();
            for (ScheduleItem item : cronograma) {
                result.add(new ProjectActivity(
                        item.id(),
                        item.titulo(),
                        item.descricao(),
                        p.id(),
                        pNome,
                        item.inicioPlanejado(),
                        item.fimPlanejado(),
                        item.status(),
                        item.responsavel(),
                        item.cor(),
                        item.criadoEm()));
            }
        }
        return result;
    }

    public ImportCsvResponse importGlobalRisksCsv(ImportRisksCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            // pula cabecalho
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
                                .atStartOfDay(java.time.ZoneOffset.UTC)
                                .toOffsetDateTime();
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

    public GlobalRisk postponeGlobalRisk(String riskId, PostponeGlobalRiskRequest request) throws IOException {
        if (request == null || request.novaDataFim() == null) {
            throw new IllegalArgumentException("Nova data de vencimento e obrigatoria para adiamento.");
        }
        if (request.motivo() == null || request.motivo().isBlank()) {
            throw new IllegalArgumentException("Motivo do adiamento e obrigatorio.");
        }
        List<GlobalRisk> all = new ArrayList<>(loadGlobalRisks());
        for (int i = 0; i < all.size(); i++) {
            GlobalRisk r = all.get(i);
            if (r.id().equals(riskId)) {
                if (!request.novaDataFim().isAfter(r.dataFim())) {
                    throw new IllegalArgumentException("A nova data deve ser posterior a data atual para caracterizar adiamento.");
                }
                List<GlobalRiskHistoryEvent> historico = new ArrayList<>(safeHistory(r.historico()));
                historico.add(historyEvent(
                        "ADIAMENTO",
                        "Prazo adiado.",
                        r.status(),
                        r.status(),
                        r.dataFim(),
                        request.novaDataFim(),
                        request.motivo().trim()));
                GlobalRisk updated = new GlobalRisk(
                        r.id(), r.titulo(), r.descricao(), r.planoAcao(), request.novaDataFim(), r.status(), r.responsavel(), historico, r.criadoEm());
                all.set(i, updated);
                saveGlobalRisks(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Apontamento de risco nao encontrado.");
    }

    private void validateRiskStatus(String status) {
        if (!status.equals("PLANO_ACAO")
                && !status.equals("DESENVOLVIMENTO")
                && !status.equals("ENTREGA")
                && !status.equals("CONCLUIDO")) {
            throw new IllegalArgumentException("Status invalido para risco.");
        }
    }

    private GlobalRisk normalizeGlobalRisk(GlobalRisk risk) {
        if (risk == null) return null;
        List<GlobalRiskHistoryEvent> historico = safeHistory(risk.historico());
        return new GlobalRisk(
                risk.id(),
                risk.titulo(),
                risk.descricao(),
                risk.planoAcao(),
                risk.dataFim(),
                risk.status(),
                risk.responsavel(),
                historico,
                risk.criadoEm());
    }

    private List<GlobalRiskHistoryEvent> safeHistory(List<GlobalRiskHistoryEvent> history) {
        return history == null ? new ArrayList<>() : history;
    }

    private List<ReplanningEvent> safeReplanList(List<ReplanningEvent> list) {
        return list == null ? new ArrayList<>() : list;
    }

    public ProjectRecord addReplanningEvent(String projectId, AddReplanningEventRequest request) throws IOException {
        if (request == null || request.scheduleItemId() == null || request.scheduleItemId().isBlank()) {
            throw new IllegalArgumentException("ID do item do cronograma e obrigatorio.");
        }
        if (request.tipoAlteracao() == null || request.tipoAlteracao().isBlank()) {
            throw new IllegalArgumentException("Tipo de alteracao e obrigatorio.");
        }
        return update(projectId, p -> {
            List<ReplanningEvent> historico = new ArrayList<>(safeReplanList(p.historicoReplanejamento()));
            historico.add(new ReplanningEvent(
                    UUID.randomUUID().toString(),
                    request.scheduleItemId(),
                    request.scheduleItemTitulo() == null ? "" : request.scheduleItemTitulo().trim(),
                    request.tipoAlteracao().trim().toUpperCase(),
                    request.inicioAnterior(),
                    request.fimAnterior(),
                    request.inicioNovo(),
                    request.fimNovo(),
                    OffsetDateTime.now()));
            log.info("Replanejamento registrado: projetoId={}, itemId={}, tipo={}", projectId, request.scheduleItemId(), request.tipoAlteracao());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), p.cronograma(), p.alocacoes(), p.financeiro(), p.riscos(), historico, p.situacao(), p.donoProjeto());
        });
    }

    private GlobalRiskHistoryEvent historyEvent(
            String tipo,
            String descricao,
            String statusAnterior,
            String statusNovo,
            OffsetDateTime dataFimAnterior,
            OffsetDateTime dataFimNova,
            String motivo) {
        return new GlobalRiskHistoryEvent(
                UUID.randomUUID().toString(),
                tipo,
                descricao,
                statusAnterior,
                statusNovo,
                dataFimAnterior,
                dataFimNova,
                motivo,
                OffsetDateTime.now());
    }

    private boolean sameDateTime(OffsetDateTime a, OffsetDateTime b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.isEqual(b);
    }

    public ProjectRecord addFinanceEntry(String projectId, CreateFinanceEntryRequest request) throws IOException {
        if (request == null || request.tipo() == null || request.valor() == null) {
            throw new IllegalArgumentException("Tipo e valor sao obrigatorios.");
        }
        String tipo = request.tipo().trim().toUpperCase();
        if (!tipo.equals("RECEITA") && !tipo.equals("DESPESA")) {
            throw new IllegalArgumentException("Tipo deve ser RECEITA ou DESPESA.");
        }
        return update(projectId, p -> {
            List<FinanceEntry> entries = new ArrayList<>(p.financeiro());
            entries.add(new FinanceEntry(
                    UUID.randomUUID().toString(),
                    tipo,
                    request.descricao() == null ? "" : request.descricao().trim(),
                    request.valor(),
                    request.dataLancamento() == null ? OffsetDateTime.now() : request.dataLancamento(),
                    OffsetDateTime.now()));
            log.info("Lancamento financeiro adicionado: projetoId={}, tipo={}, valor={}", projectId, tipo, request.valor());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), p.cronograma(), p.alocacoes(), entries, p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord addScheduleItem(String projectId, CreateScheduleItemRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank()) {
            throw new IllegalArgumentException("Titulo do item do cronograma e obrigatorio.");
        }
        return update(projectId, p -> {
            List<ScheduleItem> cronograma = new ArrayList<>(p.cronograma());
            int nextOrder = cronograma.size();
            cronograma.add(new ScheduleItem(
                    UUID.randomUUID().toString(),
                    request.titulo().trim(),
                    request.descricao() == null ? "" : request.descricao().trim(),
                    request.inicioPlanejado(),
                    request.fimPlanejado(),
                    request.permiteParalelo(),
                    "PLANEJADO",
                    nextOrder,
                    OffsetDateTime.now(),
                    request.cor(),
                    request.responsavel() == null ? null : request.responsavel().trim()));
            log.info("Item de cronograma criado: projetoId={}, titulo={}", projectId, request.titulo());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), cronograma, p.alocacoes(), p.financeiro(), p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord updateScheduleItem(String projectId, String scheduleItemId, UpdateScheduleItemRequest request) throws IOException {
        return update(projectId, p -> {
            List<ScheduleItem> cronograma = new ArrayList<>();
            boolean found = false;
            for (ScheduleItem item : p.cronograma()) {
                if (item.id().equals(scheduleItemId)) {
                    found = true;
                    cronograma.add(new ScheduleItem(
                            item.id(),
                            request.titulo() == null || request.titulo().isBlank() ? item.titulo() : request.titulo().trim(),
                            request.descricao() == null ? item.descricao() : request.descricao().trim(),
                            request.inicioPlanejado() == null ? item.inicioPlanejado() : request.inicioPlanejado(),
                            request.fimPlanejado() == null ? item.fimPlanejado() : request.fimPlanejado(),
                            request.permiteParalelo(),
                            request.status() == null || request.status().isBlank() ? item.status() : request.status().trim().toUpperCase(),
                            item.ordem(),
                            item.criadoEm(),
                            request.cor() == null ? item.cor() : request.cor(),
                            request.responsavel() == null ? item.responsavel() : request.responsavel().trim()));
                } else {
                    cronograma.add(item);
                }
            }
            if (!found) throw new IllegalArgumentException("Item de cronograma nao encontrado.");
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), cronograma, p.alocacoes(), p.financeiro(), p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord reorderSchedule(String projectId, ReorderScheduleRequest request) throws IOException {
        if (request == null || request.itemIdsOrdered() == null) {
            throw new IllegalArgumentException("Lista de ordenacao do cronograma e obrigatoria.");
        }
        return update(projectId, p -> {
            List<ScheduleItem> existing = new ArrayList<>(p.cronograma());
            if (existing.size() != request.itemIdsOrdered().size()) {
                throw new IllegalArgumentException("A lista enviada nao corresponde ao total de itens.");
            }
            List<ScheduleItem> reordered = new ArrayList<>();
            for (int i = 0; i < request.itemIdsOrdered().size(); i++) {
                String id = request.itemIdsOrdered().get(i);
                ScheduleItem item = existing.stream().filter(s -> s.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Item de cronograma invalido na ordenacao."));
                reordered.add(new ScheduleItem(
                        item.id(),
                        item.titulo(),
                        item.descricao(),
                        item.inicioPlanejado(),
                        item.fimPlanejado(),
                        item.permiteParalelo(),
                        item.status(),
                        i,
                        item.criadoEm(),
                        item.cor(),
                        item.responsavel()));
            }
            log.info("Cronograma reordenado: projetoId={}, totalItens={}", projectId, reordered.size());
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), reordered, p.alocacoes(), p.financeiro(), p.riscos(), safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord addRiskItem(String projectId, CreateRiskItemRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank()) {
            throw new IllegalArgumentException("Titulo do apontamento de risco e obrigatorio.");
        }
        if (request.dataFim() == null) {
            throw new IllegalArgumentException("Data fim do apontamento de risco e obrigatoria.");
        }
        return update(projectId, p -> {
            List<RiskItem> riscos = new ArrayList<>(p.riscos() == null ? List.of() : p.riscos());
            riscos.add(new RiskItem(
                    UUID.randomUUID().toString(),
                    request.titulo().trim(),
                    request.descricao() == null ? "" : request.descricao().trim(),
                    request.dataFim(),
                    "PLANO_ACAO",
                    OffsetDateTime.now()));
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), p.cronograma(), p.alocacoes(), p.financeiro(), riscos, safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    public ProjectRecord updateRiskItemStatus(String projectId, String riskId, UpdateRiskItemStatusRequest request) throws IOException {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException("Status do risco e obrigatorio.");
        }
        String nextStatus = request.status().trim().toUpperCase();
        if (!nextStatus.equals("PLANO_ACAO")
                && !nextStatus.equals("DESENVOLVIMENTO")
                && !nextStatus.equals("ENTREGA")
                && !nextStatus.equals("CONCLUIDO")) {
            throw new IllegalArgumentException("Status invalido para risco.");
        }

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
            return new ProjectRecord(p.id(), p.nome(), p.descricao(), p.criadoEm(), p.etapas(), p.cronograma(), p.alocacoes(), p.financeiro(), riscos, safeReplanList(p.historicoReplanejamento()), p.situacao(), p.donoProjeto());
        });
    }

    // ── Gantt etapas parser (mirrors frontend ET_TAG logic) ──────────────────
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

    /** Effective completion % for a single ScheduleItem, mirroring the Gantt panel logic. */
    private double effectivePctItem(ScheduleItem item,
                                    java.util.Map<String, GanttItemMeta> metaMap) {
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

    public List<ProjectOverviewResponse> overview(String username, String role) throws IOException {
        // Load gantt configs once for all projects (avoid per-project I/O in the loop)
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
                // No activities yet: fall back to checklist steps
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

            String status = percentualConclusao >= 100 ? "CONCLUIDO" : (percentualConclusao >= 50 ? "EM_ANDAMENTO" : "PLANEJADO");
            BigDecimal custoEquipe = p.alocacoes().stream()
                    .map(Allocation::custoPlanejado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal receitas = totalByType(p.financeiro(), "RECEITA");
            BigDecimal despesas = totalByType(p.financeiro(), "DESPESA");
            out.add(new ProjectOverviewResponse(
                    p.id(),
                    p.nome(),
                    p.descricao(),
                    status,
                    percentualConclusao,
                    totalEtapas,
                    etapasConcluidas,
                    p.alocacoes().size(),
                    custoEquipe,
                    receitas,
                    despesas,
                    receitas.subtract(despesas),
                    p.situacao() != null ? p.situacao() : "PUBLISHED",
                    p.donoProjeto()));
        }
        return out;
    }

    public List<Person> listPeople() throws IOException {
        return loadPeople().stream().map(this::normalizePerson).toList();
    }

    private Person normalizePerson(Person p) {
        if (p.ativo() != null) return p;
        return new Person(p.id(), p.nome(), p.perfilId(), p.perfilNome(),
                p.tipoVinculo(), p.consultoria(), p.valorHora(), p.valorMensal(),
                p.vagaUrl(), p.vagaAlias(), p.dataNascimento(), p.contato(),
                true,  // legacy records default to active
                p.vagasAnteriores() != null ? p.vagasAnteriores() : java.util.List.of(),
                p.criadoEm());
    }

    public List<Consultancy> listConsultancies() throws IOException {
        List<Consultancy> all = loadConsultancies();
        List<Consultancy> normalized = new ArrayList<>(all.size());
        for (Consultancy c : all) {
            normalized.add(normalizeConsultancy(c));
        }
        return normalized;
    }

    public Consultancy createConsultancy(CreateConsultancyRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do prestador de servico e obrigatorio.");
        }
        List<Consultancy> all = new ArrayList<>(loadConsultancies());
        boolean duplicate = all.stream().anyMatch(c -> c.nome().equalsIgnoreCase(request.nome().trim()));
        if (duplicate) {
            throw new IllegalArgumentException("Ja existe prestador de servico com esse nome.");
        }
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
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("telefone")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 4) {
                ignorados++;
                continue;
            }
            try {
                createConsultancy(new CreateConsultancyRequest(
                        stripQuotes(cols[0]),
                        stripQuotes(cols[1]),
                        stripQuotes(cols[2]),
                        stripQuotes(cols[3]),
                        ""));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public Consultancy updateConsultancy(String consultancyId, CreateConsultancyRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do prestador de servico e obrigatorio.");
        }
        List<Consultancy> all = new ArrayList<>(loadConsultancies());
        for (int i = 0; i < all.size(); i++) {
            Consultancy c = all.get(i);
            if (c.id().equals(consultancyId)) {
                Consultancy updated = new Consultancy(
                        c.id(),
                        request.nome().trim(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        resolveTelefone(request),
                        resolveEmail(request),
                        "",
                        c.criadoEm());
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
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("responsavel")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 4) {
                ignorados++;
                continue;
            }
            try {
                createFocalPoint(new CreateFocalPointRequest(
                        stripQuotes(cols[0]),
                        stripQuotes(cols[1]),
                        stripQuotes(cols[2]),
                        stripQuotes(cols[3])));
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
                        current.id(),
                        request.area().trim(),
                        request.responsavelPor().trim(),
                        request.email().trim(),
                        request.telefone().trim(),
                        current.criadoEm());
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

    public Person createPerson(CreatePersonRequest request) throws IOException {
        validatePersonRequest(request);
        Profile profile = loadProfiles().stream()
                .filter(p -> p.id().equals(request.perfilId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil da pessoa nao encontrado."));
        BigDecimal valorHoraCalculado = resolveValorHora(request, profile);
        BigDecimal valorMensalAplicado = resolveValorMensal(request, profile, valorHoraCalculado);
        Person created = new Person(
                UUID.randomUUID().toString(),
                request.nome().trim(),
                profile.id(),
                profile.nomePerfil(),
                request.tipoVinculo().trim().toUpperCase(),
                request.consultoria() == null ? "" : request.consultoria().trim(),
                valorHoraCalculado,
                valorMensalAplicado,
                request.vagaUrl() == null ? null : request.vagaUrl().trim(),
                request.vagaAlias() == null ? null : request.vagaAlias().trim(),
                request.dataNascimento() == null ? null : request.dataNascimento().trim(),
                request.contato() == null ? null : request.contato().trim(),
                request.ativo(),
                request.vagasAnteriores() != null ? request.vagasAnteriores() : java.util.List.of(),
                OffsetDateTime.now());
        List<Person> all = new ArrayList<>(loadPeople());
        all.add(created);
        savePeople(all);
        return created;
    }

    public Person updatePerson(String personId, CreatePersonRequest request) throws IOException {
        validatePersonRequest(request);
        Profile profile = loadProfiles().stream()
                .filter(p -> p.id().equals(request.perfilId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil da pessoa nao encontrado."));
        BigDecimal valorHoraCalculado = resolveValorHora(request, profile);
        BigDecimal valorMensalAplicado = resolveValorMensal(request, profile, valorHoraCalculado);
        List<Person> all = new ArrayList<>(loadPeople());
        for (int i = 0; i < all.size(); i++) {
            Person p = all.get(i);
            if (p.id().equals(personId)) {
                Person updated = new Person(
                        p.id(),
                        request.nome().trim(),
                        profile.id(),
                        profile.nomePerfil(),
                        request.tipoVinculo().trim().toUpperCase(),
                        request.consultoria() == null ? "" : request.consultoria().trim(),
                        valorHoraCalculado,
                        valorMensalAplicado,
                        request.vagaUrl() == null ? p.vagaUrl() : request.vagaUrl().trim(),
                        request.vagaAlias() == null ? p.vagaAlias() : request.vagaAlias().trim(),
                        request.dataNascimento() == null ? p.dataNascimento() : request.dataNascimento().trim(),
                        request.contato() == null ? p.contato() : request.contato().trim(),
                        request.ativo(),
                        request.vagasAnteriores() != null ? request.vagasAnteriores()
                                : (p.vagasAnteriores() != null ? p.vagasAnteriores() : java.util.List.of()),
                        p.criadoEm());
                all.set(i, updated);
                savePeople(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Pessoa nao encontrada.");
    }

    public void deletePerson(String personId) throws IOException {
        List<Person> all = new ArrayList<>(loadPeople());
        // Find and remove the person
        Person deleted = all.stream()
                .filter(p -> p.id().equals(personId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pessoa nao encontrada."));
        all.remove(deleted);
        savePeople(all);

        // Cascade: desassocia o responsavel das atividades de cronograma
        String nome = deleted.nome();
        List<ProjectRecord> projects = new ArrayList<>(load());
        boolean projChanged = false;
        for (int pi = 0; pi < projects.size(); pi++) {
            ProjectRecord proj = projects.get(pi);
            List<ScheduleItem> items = proj.cronograma();
            if (items == null || items.isEmpty()) continue;
            List<ScheduleItem> newItems = new ArrayList<>(items.size());
            boolean itemChanged = false;
            for (ScheduleItem item : items) {
                if (nome != null && nome.equals(item.responsavel())) {
                    newItems.add(new ScheduleItem(
                            item.id(), item.titulo(), item.descricao(),
                            item.inicioPlanejado(), item.fimPlanejado(),
                            item.permiteParalelo(), item.status(), item.ordem(),
                            item.criadoEm(), item.cor(), null));
                    itemChanged = true;
                } else {
                    newItems.add(item);
                }
            }
            if (itemChanged) {
                projects.set(pi, new ProjectRecord(
                        proj.id(), proj.nome(), proj.descricao(), proj.criadoEm(),
                        proj.etapas(), newItems, proj.alocacoes(), proj.financeiro(),
                        proj.riscos(), safeReplanList(proj.historicoReplanejamento()),
                        proj.situacao(), proj.donoProjeto()));
                projChanged = true;
            }
        }
        if (projChanged) save(projects);
    }

    public ImportPeopleCsvResponse importPeopleCsv(ImportPeopleCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }

        List<Profile> profiles = loadProfiles();
        int criados = 0;
        int ignorados = 0;

        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("nome")) continue;

            String[] cols = splitCsvLine(line);
            if (cols.length < 4) {
                ignorados++;
                continue;
            }

            String nome = stripQuotes(cols[0]);
            String perfilNome = stripQuotes(cols[1]);
            String tipoVinculo = stripQuotes(cols[2]);
            String consultoria = cols.length > 3 ? stripQuotes(cols[3]) : "";
            BigDecimal valorHora = parseDecimal(cols.length > 4 ? cols[4] : "");
            BigDecimal valorMensal = parseDecimal(cols.length > 5 ? cols[5] : "");

            Profile profile = profiles.stream()
                    .filter(p -> p.nomePerfil() != null && p.nomePerfil().equalsIgnoreCase(perfilNome))
                    .findFirst()
                    .orElse(null);
            if (profile == null) {
                ignorados++;
                continue;
            }

            String vagaUrl   = cols.length > 6 ? stripQuotes(cols[6]) : null;
            String vagaAlias = cols.length > 7 ? stripQuotes(cols[7]) : null;
            try {
                createPerson(new CreatePersonRequest(
                        nome,
                        profile.id(),
                        tipoVinculo,
                        consultoria,
                        valorHora,
                        valorMensal,
                        vagaUrl,
                        vagaAlias,
                        null,   // dataNascimento
                        null,   // contato
                        true,   // ativo
                        null)); // vagasAnteriores
                criados++;
            } catch (IllegalArgumentException ex) {
                ignorados++;
            }
        }

        return new ImportPeopleCsvResponse(criados, ignorados);
    }

    public List<MonthlyHours> listMonthlyHours() throws IOException {
        List<MonthlyHours> hours = new ArrayList<>(loadMonthlyHours());
        hours.sort(Comparator.comparingInt(MonthlyHours::mes));
        return hours;
    }

    public List<AllocationPaymentState> listAllocationPayments() throws IOException {
        List<AllocationPaymentState> all = new ArrayList<>(loadAllocationPayments());
        all.sort(Comparator
                .comparing(AllocationPaymentState::allocationId, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(AllocationPaymentState::month));
        return all;
    }

    public AllocationPaymentState upsertAllocationPayment(String allocationId, int month, boolean paid, String username) throws IOException {
        if (allocationId == null || allocationId.isBlank()) {
            throw new IllegalArgumentException("AllocationId e obrigatorio.");
        }
        if (month < 0 || month > 11) {
            throw new IllegalArgumentException("Mes invalido. Use 0..11.");
        }
        String user = username == null ? "" : username;
        AllocationPaymentState updated = new AllocationPaymentState(
                allocationId.trim(),
                month,
                paid,
                OffsetDateTime.now(),
                user);

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

    public List<AllocationMonthlyState> listAllocationMonthlyStates() throws IOException {
        List<AllocationMonthlyState> all = new ArrayList<>(loadAllocationMonthlyStates());
        all.sort(Comparator
                .comparing(AllocationMonthlyState::allocationId, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(AllocationMonthlyState::month));
        return all;
    }

    public AllocationMonthlyState upsertAllocationMonthlyState(
            String allocationId,
            int month,
            UpdateAllocationMonthlyStateRequest request,
            String username) throws IOException {
        if (allocationId == null || allocationId.isBlank()) throw new IllegalArgumentException("AllocationId e obrigatorio.");
        if (month < 0 || month > 11) throw new IllegalArgumentException("Mes invalido. Use 0..11.");
        String user = username == null ? "" : username.trim();
        String alloc = allocationId.trim();
        BigDecimal manualValue = request != null ? request.manualValue() : null;
        BigDecimal manualPercent = request != null ? request.manualPercent() : null;
        AllocationMonthlyState updated = new AllocationMonthlyState(
                alloc,
                month,
                request != null ? request.canceled() : null,
                manualValue,
                manualPercent,
                OffsetDateTime.now(),
                user);
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
        if (request == null || request.loId() == null || request.loId().isBlank()) {
            throw new IllegalArgumentException("LoId e obrigatorio.");
        }
        if (request.x() == null || request.y() == null) {
            throw new IllegalArgumentException("Posicao do cursor e obrigatoria.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Usuario nao autenticado.");
        }
        BigDecimal x = request.x().max(BigDecimal.ZERO).min(BigDecimal.ONE);
        BigDecimal y = request.y().max(BigDecimal.ZERO).min(BigDecimal.ONE);
        String lo = request.loId().trim();
        String user = username.trim();
        AllocationCursorState updated = new AllocationCursorState(user, lo, x, y, OffsetDateTime.now());
        List<AllocationCursorState> all = new ArrayList<>(loadAllocationCursors());
        all.removeIf(c -> c != null && user.equalsIgnoreCase(c.username()) && lo.equals(c.loId()));
        all.add(updated);
        saveAllocationCursors(all);
        return updated;
    }

    public MonthlyHours upsertMonthlyHours(int month, UpsertMonthlyHoursRequest request) throws IOException {
        if (month < 1 || month > 12) throw new IllegalArgumentException("Mes invalido. Use 1..12.");
        if (request == null || request.horas() == null || request.horas().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Horas do mes deve ser maior que zero.");
        }
        List<MonthlyHours> all = new ArrayList<>(loadMonthlyHours());
        for (int i = 0; i < all.size(); i++) {
            MonthlyHours h = all.get(i);
            if (h.mes() == month) {
                MonthlyHours updated = new MonthlyHours(month, request.horas(), OffsetDateTime.now());
                all.set(i, updated);
                saveMonthlyHours(all);
                return updated;
            }
        }
        MonthlyHours created = new MonthlyHours(month, request.horas(), OffsetDateTime.now());
        all.add(created);
        saveMonthlyHours(all);
        return created;
    }

    private BigDecimal totalByType(List<FinanceEntry> entries, String type) {
        return entries.stream()
                .filter(e -> type.equals(e.tipo()))
                .map(FinanceEntry::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private List<ProjectRecord> load() throws IOException {
        return projectStorePort.loadProjects();
    }

    private void save(List<ProjectRecord> projects) throws IOException {
        projectStorePort.saveProjects(projects);
    }

    private List<Profile> loadProfiles() throws IOException {
        return jsonStore.readList(profilesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveProfiles(List<Profile> profiles) throws IOException {
        jsonStore.writeList(profilesPath, profiles);
    }

    private List<BudgetLine> loadBudgetLines() throws IOException {
        List<BudgetLine> all = jsonStore.readList(budgetLinesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        int anoAtual = LocalDate.now().getYear();
        boolean changed = false;
        List<BudgetLine> normalized = new ArrayList<>(all.size());
        for (BudgetLine lo : all) {
            int ano = lo.ano() <= 0 ? anoAtual : lo.ano();
            if (ano != lo.ano()) changed = true;
            normalized.add(new BudgetLine(
                    lo.id(),
                    lo.codigo(),
                    lo.nome(),
                    ano,
                    lo.tipo(),
                    lo.centroCusto(),
                    lo.valorTotal(),
                    lo.criadoEm(),
                    lo.situacao(),
                    lo.dono()));
        }
        if (changed) {
            saveBudgetLines(normalized);
        }
        return normalized;
    }

    private void saveBudgetLines(List<BudgetLine> budgetLines) throws IOException {
        jsonStore.writeList(budgetLinesPath, budgetLines);
    }

    private List<BusinessEpic> loadBusinessEpics() throws IOException {
        return jsonStore.readList(businessEpicsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveBusinessEpics(List<BusinessEpic> businessEpics) throws IOException {
        jsonStore.writeList(businessEpicsPath, businessEpics);
    }

    private List<BudgetAllocation> loadBudgetAllocations() throws IOException {
        return jsonStore.readList(budgetAllocationsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveBudgetAllocations(List<BudgetAllocation> budgetAllocations) throws IOException {
        jsonStore.writeList(budgetAllocationsPath, budgetAllocations);
    }

    private List<BudgetLineAdjustment> loadBudgetLineAdjustments() throws IOException {
        return jsonStore.readList(budgetLineAdjustmentsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveBudgetLineAdjustments(List<BudgetLineAdjustment> adjustments) throws IOException {
        jsonStore.writeList(budgetLineAdjustmentsPath, adjustments);
    }

    private List<GlobalRisk> loadGlobalRisks() throws IOException {
        return jsonStore.readList(globalRisksPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveGlobalRisks(List<GlobalRisk> risks) throws IOException {
        jsonStore.writeList(globalRisksPath, risks);
    }

    private List<Person> loadPeople() throws IOException {
        return jsonStore.readList(peoplePath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void savePeople(List<Person> people) throws IOException {
        jsonStore.writeList(peoplePath, people);
    }

    // ── Absences ─────────────────────────────────────────────────────────────
    public List<Absence> listAbsences() throws IOException {
        return loadAbsences();
    }

    public Absence createAbsence(CreateAbsenceRequest request) throws IOException {
        if (request == null || request.pessoaId() == null || request.pessoaId().isBlank())
            throw new IllegalArgumentException("ID da pessoa e obrigatorio.");
        if (request.tipo() == null || request.tipo().isBlank())
            throw new IllegalArgumentException("Tipo de ausencia e obrigatorio.");
        if (request.inicio() == null || request.inicio().isBlank())
            throw new IllegalArgumentException("Data de inicio e obrigatoria.");
        if (request.fim() == null || request.fim().isBlank())
            throw new IllegalArgumentException("Data de fim e obrigatoria.");
        Absence absence = new Absence(
                UUID.randomUUID().toString(),
                request.pessoaId().trim(),
                request.pessoaNome() == null ? "" : request.pessoaNome().trim(),
                request.tipo().trim().toUpperCase(),
                request.inicio().trim(),
                request.fim().trim(),
                request.recorrente(),
                request.observacao() == null ? "" : request.observacao().trim(),
                OffsetDateTime.now());
        List<Absence> all = new ArrayList<>(loadAbsences());
        all.add(absence);
        saveAbsences(all);
        log.info("Ausencia criada: id={}, pessoa={}, tipo={}, inicio={}, fim={}", absence.id(), absence.pessoaNome(), absence.tipo(), absence.inicio(), absence.fim());
        return absence;
    }

    public Absence updateAbsence(String absenceId, CreateAbsenceRequest request) throws IOException {
        if (request == null) throw new IllegalArgumentException("Request invalido.");
        List<Absence> all = new ArrayList<>(loadAbsences());
        for (int i = 0; i < all.size(); i++) {
            Absence a = all.get(i);
            if (a.id().equals(absenceId)) {
                Absence updated = new Absence(
                        a.id(),
                        request.pessoaId() == null ? a.pessoaId() : request.pessoaId().trim(),
                        request.pessoaNome() == null ? a.pessoaNome() : request.pessoaNome().trim(),
                        request.tipo() == null ? a.tipo() : request.tipo().trim().toUpperCase(),
                        request.inicio() == null ? a.inicio() : request.inicio().trim(),
                        request.fim() == null ? a.fim() : request.fim().trim(),
                        request.recorrente(),
                        request.observacao() == null ? a.observacao() : request.observacao().trim(),
                        a.criadoEm());
                all.set(i, updated);
                saveAbsences(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Ausencia nao encontrada.");
    }

    public void deleteAbsence(String absenceId) throws IOException {
        List<Absence> all = new ArrayList<>(loadAbsences());
        boolean removed = all.removeIf(a -> a.id().equals(absenceId));
        if (!removed) throw new IllegalArgumentException("Ausencia nao encontrada.");
        saveAbsences(all);
    }

    private List<Absence> loadAbsences() throws IOException {
        return jsonStore.readList(absencesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAbsences(List<Absence> absences) throws IOException {
        jsonStore.writeList(absencesPath, absences);
    }

    // ── Incidents ─────────────────────────────────────────────────────────────
    public List<Incident> listIncidents() throws IOException {
        return loadIncidents().stream().map(this::normalizeIncident).toList();
    }

    public Incident createIncident(CreateIncidentRequest request) throws IOException {
        validateIncidentRequest(request);
        String initialStatus = normalizeEnum(request.status(), "ABERTO");
        List<IncidentHistoryEvent> historico = new ArrayList<>();
        historico.add(new IncidentHistoryEvent(
                UUID.randomUUID().toString(),
                "CRIACAO",
                "Incidente criado.",
                null,
                initialStatus,
                OffsetDateTime.now()));
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
                historico,
                OffsetDateTime.now());
        List<Incident> all = new ArrayList<>(loadIncidents());
        all.add(created);
        saveIncidents(all);
        log.info("Incidente criado: id={}, titulo={}, severidade={}", created.id(), created.titulo(), created.severidade());
        return created;
    }

    public ImportCsvResponse importIncidentsCsv(ImportIncidentsCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("titulo")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 1) {
                ignorados++;
                continue;
            }
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
                    historico.add(new IncidentHistoryEvent(
                            UUID.randomUUID().toString(),
                            "STATUS",
                            "Status alterado.",
                            current.status(),
                            newStatus,
                            OffsetDateTime.now()));
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
                        historico,
                        current.criadoEm());
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

    private void validateIncidentRequest(CreateIncidentRequest request) {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do incidente e obrigatorio.");
    }

    private List<Incident> loadIncidents() throws IOException {
        return jsonStore.readList(incidentsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveIncidents(List<Incident> incidents) throws IOException {
        jsonStore.writeList(incidentsPath, incidents);
    }

    // ── Technical Debt ────────────────────────────────────────────────────────
    public List<TechnicalDebt> listTechnicalDebts() throws IOException {
        return loadTechnicalDebts().stream().map(this::normalizeDebt).toList();
    }

    public TechnicalDebt createTechnicalDebt(CreateTechnicalDebtRequest request) throws IOException {
        validateTechnicalDebtRequest(request);
        String initialStatus = normalizeEnum(request.status(), "IDENTIFICADO");
        List<TechnicalDebtHistoryEvent> historico = new ArrayList<>();
        historico.add(new TechnicalDebtHistoryEvent(
                UUID.randomUUID().toString(),
                "CRIACAO",
                "Débito técnico registrado.",
                null,
                initialStatus,
                OffsetDateTime.now()));
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
                historico,
                OffsetDateTime.now());
        List<TechnicalDebt> all = new ArrayList<>(loadTechnicalDebts());
        all.add(created);
        saveTechnicalDebts(all);
        log.info("Debito tecnico criado: id={}, titulo={}, prioridade={}", created.id(), created.titulo(), created.prioridade());
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
                    historico.add(new TechnicalDebtHistoryEvent(
                            UUID.randomUUID().toString(),
                            "STATUS",
                            "Status alterado.",
                            current.status(),
                            newStatus,
                            OffsetDateTime.now()));
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
                        historico,
                        current.criadoEm());
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

    private void validateTechnicalDebtRequest(CreateTechnicalDebtRequest request) {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do debito tecnico e obrigatorio.");
    }

    private List<TechnicalDebt> loadTechnicalDebts() throws IOException {
        return jsonStore.readList(technicalDebtsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveTechnicalDebts(List<TechnicalDebt> debts) throws IOException {
        jsonStore.writeList(technicalDebtsPath, debts);
    }

    // ── Indicators ────────────────────────────────────────────────────────────
    public List<Indicator> listIndicators() throws IOException {
        return loadIndicators().stream().map(this::normalizeIndicator).toList();
    }

    public Indicator createIndicator(CreateIndicatorRequest request) throws IOException {
        if (request == null || request.titulo() == null || request.titulo().isBlank())
            throw new IllegalArgumentException("Titulo do indicador e obrigatorio.");
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
                new ArrayList<>(),
                new ArrayList<>(),
                OffsetDateTime.now());
        List<Indicator> all = new ArrayList<>(loadIndicators());
        all.add(created);
        saveIndicators(all);
        log.info("Indicador criado: id={}, titulo={}, tipo={}", created.id(), created.titulo(), created.tipo());
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
                        curr.ciclos(),
                        curr.acoes(),
                        curr.criadoEm());
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

    public Indicator addIndicatorCycle(String indicatorId, CreateIndicatorCycleRequest request) throws IOException {
        if (request == null || request.valor() == null)
            throw new IllegalArgumentException("Valor do ciclo e obrigatorio.");
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                List<IndicatorCycle> ciclos = new ArrayList<>(safeIndList(curr.ciclos()));
                int nextNum = ciclos.stream().mapToInt(IndicatorCycle::numero).max().orElse(0) + 1;
                ciclos.add(new IndicatorCycle(
                        UUID.randomUUID().toString(),
                        nextNum,
                        request.valor(),
                        request.observacao() == null ? "" : request.observacao().trim(),
                        request.dataReferencia() == null ? OffsetDateTime.now() : request.dataReferencia(),
                        OffsetDateTime.now()));
                // Mark specified actions as concluded in this cycle
                List<IndicatorAction> acoes = new ArrayList<>(safeIndList(curr.acoes()));
                java.util.Set<String> toClose = request.acoesConcluidasIds() == null
                        ? java.util.Set.of()
                        : new java.util.HashSet<>(request.acoesConcluidasIds());
                for (int j = 0; j < acoes.size(); j++) {
                    IndicatorAction a = acoes.get(j);
                    if (toClose.contains(a.id()) && "ABERTA".equals(a.status())) {
                        acoes.set(j, new IndicatorAction(
                                a.id(), a.descricao(), a.responsavel(), "CONCLUIDA",
                                a.cicloAberto(), nextNum, a.prazo(), OffsetDateTime.now(), a.criadoEm()));
                    }
                }
                Indicator updated = new Indicator(
                        curr.id(), curr.titulo(), curr.descricao(), curr.tipo(), curr.categoria(),
                        curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, acoes, curr.criadoEm());
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
                List<IndicatorCycle> ciclos = new ArrayList<>(safeIndList(curr.ciclos()));
                boolean found = false;
                for (int j = 0; j < ciclos.size(); j++) {
                    IndicatorCycle c = ciclos.get(j);
                    if (c.id().equals(cycleId)) {
                        ciclos.set(j, new IndicatorCycle(
                                c.id(), c.numero(), request.valor(),
                                request.observacao() == null ? c.observacao() : request.observacao().trim(),
                                request.dataReferencia() == null ? c.dataReferencia() : request.dataReferencia(),
                                c.criadoEm()));
                        found = true;
                        break;
                    }
                }
                if (!found) throw new IllegalArgumentException("Ciclo nao encontrado.");
                Indicator updated = new Indicator(
                        curr.id(), curr.titulo(), curr.descricao(), curr.tipo(), curr.categoria(),
                        curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, safeIndList(curr.acoes()), curr.criadoEm());
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
                List<IndicatorCycle> ciclos = new ArrayList<>(safeIndList(curr.ciclos()));
                boolean removed = ciclos.removeIf(c -> c.id().equals(cycleId));
                if (!removed) throw new IllegalArgumentException("Ciclo nao encontrado.");
                Indicator updated = new Indicator(
                        curr.id(), curr.titulo(), curr.descricao(), curr.tipo(), curr.categoria(),
                        curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, safeIndList(curr.acoes()), curr.criadoEm());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    public Indicator addIndicatorAction(String indicatorId, CreateIndicatorActionRequest request) throws IOException {
        if (request == null || request.descricao() == null || request.descricao().isBlank())
            throw new IllegalArgumentException("Descricao da acao e obrigatoria.");
        List<Indicator> all = new ArrayList<>(loadIndicators());
        for (int i = 0; i < all.size(); i++) {
            Indicator curr = all.get(i);
            if (curr.id().equals(indicatorId)) {
                List<IndicatorCycle> ciclos = safeIndList(curr.ciclos());
                int currentCycle = ciclos.stream().mapToInt(IndicatorCycle::numero).max().orElse(0);
                List<IndicatorAction> acoes = new ArrayList<>(safeIndList(curr.acoes()));
                acoes.add(new IndicatorAction(
                        UUID.randomUUID().toString(),
                        request.descricao().trim(),
                        request.responsavel() == null ? "" : request.responsavel().trim(),
                        "ABERTA",
                        currentCycle,
                        null,
                        request.prazo(),
                        null,
                        OffsetDateTime.now()));
                Indicator updated = new Indicator(
                        curr.id(), curr.titulo(), curr.descricao(), curr.tipo(), curr.categoria(),
                        curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, acoes, curr.criadoEm());
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
                List<IndicatorCycle> ciclos = safeIndList(curr.ciclos());
                int currentCycle = ciclos.stream().mapToInt(IndicatorCycle::numero).max().orElse(0);
                List<IndicatorAction> acoes = new ArrayList<>(safeIndList(curr.acoes()));
                boolean found = false;
                for (int j = 0; j < acoes.size(); j++) {
                    IndicatorAction a = acoes.get(j);
                    if (a.id().equals(actionId)) {
                        found = true;
                        String newStatus = request.status() == null || request.status().isBlank() ? a.status() : request.status().trim().toUpperCase();
                        boolean concluding = "CONCLUIDA".equals(newStatus) && !"CONCLUIDA".equals(a.status());
                        acoes.set(j, new IndicatorAction(
                                a.id(),
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
                Indicator updated = new Indicator(
                        curr.id(), curr.titulo(), curr.descricao(), curr.tipo(), curr.categoria(),
                        curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), ciclos, acoes, curr.criadoEm());
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
                List<IndicatorAction> acoes = new ArrayList<>(safeIndList(curr.acoes()));
                boolean removed = acoes.removeIf(a -> a.id().equals(actionId));
                if (!removed) throw new IllegalArgumentException("Acao nao encontrada.");
                Indicator updated = new Indicator(
                        curr.id(), curr.titulo(), curr.descricao(), curr.tipo(), curr.categoria(),
                        curr.unidade(), curr.meta(), curr.polaridade(), curr.frequencia(),
                        curr.responsavel(), curr.status(), curr.ciclos(), acoes, curr.criadoEm());
                all.set(i, updated);
                saveIndicators(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("Indicador nao encontrado.");
    }

    private Indicator normalizeIndicator(Indicator ind) {
        if (ind.ciclos() != null && ind.acoes() != null) return ind;
        return new Indicator(
                ind.id(), ind.titulo(), ind.descricao(), ind.tipo(), ind.categoria(),
                ind.unidade(), ind.meta(), ind.polaridade(), ind.frequencia(),
                ind.responsavel(), ind.status(),
                ind.ciclos() == null ? new ArrayList<>() : ind.ciclos(),
                ind.acoes() == null ? new ArrayList<>() : ind.acoes(),
                ind.criadoEm());
    }

    private <T> List<T> safeIndList(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private List<Indicator> loadIndicators() throws IOException {
        return jsonStore.readList(indicatorsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveIndicators(List<Indicator> indicators) throws IOException {
        jsonStore.writeList(indicatorsPath, indicators);
    }

    private static String normalizeEnum(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toUpperCase();
    }

    private List<IncidentHistoryEvent> safeIncidentHistory(List<IncidentHistoryEvent> history) {
        return history == null ? new ArrayList<>() : new ArrayList<>(history);
    }

    private List<TechnicalDebtHistoryEvent> safeDebtHistory(List<TechnicalDebtHistoryEvent> history) {
        return history == null ? new ArrayList<>() : new ArrayList<>(history);
    }

    private Incident normalizeIncident(Incident i) {
        if (i.historico() != null) return i;
        return new Incident(
                i.id(), i.titulo(), i.descricao(), i.tipo(), i.severidade(), i.status(),
                i.responsavel(), i.dataOcorrencia(), i.dataResolucao(), i.impacto(),
                i.causaRaiz(), i.acoesCorrativas(), new ArrayList<>(), i.criadoEm());
    }

    private TechnicalDebt normalizeDebt(TechnicalDebt d) {
        if (d.historico() != null) return d;
        return new TechnicalDebt(
                d.id(), d.titulo(), d.descricao(), d.categoria(), d.impacto(),
                d.esforcoEstimado(), d.prioridade(), d.status(), d.responsavel(),
                d.projetoRef(), d.dataAlvo(), d.resolvidoEm(), new ArrayList<>(), d.criadoEm());
    }

    private List<MonthlyHours> loadMonthlyHours() throws IOException {
        return jsonStore.readList(monthlyHoursPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveMonthlyHours(List<MonthlyHours> hours) throws IOException {
        jsonStore.writeList(monthlyHoursPath, hours);
    }

    private List<AllocationPaymentState> loadAllocationPayments() throws IOException {
        return jsonStore.readList(allocationPaymentsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAllocationPayments(List<AllocationPaymentState> states) throws IOException {
        jsonStore.writeList(allocationPaymentsPath, states);
    }

    private List<LoPresenceState> loadLoPresence() throws IOException {
        return jsonStore.readList(loPresencePath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveLoPresence(List<LoPresenceState> states) throws IOException {
        jsonStore.writeList(loPresencePath, states);
    }

    private List<AllocationMonthlyState> loadAllocationMonthlyStates() throws IOException {
        return jsonStore.readList(allocationMonthlyStatePath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAllocationMonthlyStates(List<AllocationMonthlyState> states) throws IOException {
        jsonStore.writeList(allocationMonthlyStatePath, states);
    }

    private List<AllocationCursorState> loadAllocationCursors() throws IOException {
        return jsonStore.readList(allocationCursorsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAllocationCursors(List<AllocationCursorState> states) throws IOException {
        jsonStore.writeList(allocationCursorsPath, states);
    }

    private List<Consultancy> loadConsultancies() throws IOException {
        return jsonStore.readList(consultanciesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveConsultancies(List<Consultancy> consultancies) throws IOException {
        jsonStore.writeList(consultanciesPath, consultancies);
    }

    private List<FocalPoint> loadFocalPoints() throws IOException {
        return jsonStore.readList(focalPointsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveFocalPoints(List<FocalPoint> focalPoints) throws IOException {
        jsonStore.writeList(focalPointsPath, focalPoints);
    }

    // ── Feriados ──────────────────────────────────────────────────────────────

    private static final java.util.List<Boolean> DEFAULT_DIAS_UTEIS =
            java.util.List.of(false, true, true, true, true, true, false);

    public FeriadosConfig getFeriadosConfig() throws IOException {
        FeriadosConfig cfg = jsonStore.readObject(feriadosPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        return new FeriadosConfig(
                cfg == null || cfg.feriados() == null ? new ArrayList<>() : cfg.feriados(),
                cfg == null || cfg.federalOverrides() == null ? new java.util.HashMap<>() : cfg.federalOverrides(),
                cfg == null || cfg.diasUteis() == null ? DEFAULT_DIAS_UTEIS : cfg.diasUteis());
    }

    public FeriadosConfig saveFeriadosConfig(FeriadosConfig config) throws IOException {
        FeriadosConfig toSave = new FeriadosConfig(
                config == null || config.feriados() == null ? new ArrayList<>() : config.feriados(),
                config == null || config.federalOverrides() == null ? new java.util.HashMap<>() : config.federalOverrides(),
                config == null || config.diasUteis() == null ? DEFAULT_DIAS_UTEIS : config.diasUteis());
        jsonStore.writeObject(feriadosPath, toSave);
        return toSave;
    }

    // ── Gantt Configuration ───────────────────────────────────────────────────

    public GanttProjectConfig getGanttConfig(String projectId) throws IOException {
        return loadGanttConfigs().stream()
                .filter(c -> projectId.equals(c.projectId()))
                .findFirst()
                .orElse(new GanttProjectConfig(projectId, new ArrayList<>(), new java.util.HashMap<>()));
    }

    public GanttProjectConfig saveGanttConfig(String projectId, SaveGanttConfigRequest request) throws IOException {
        GanttProjectConfig newConfig = new GanttProjectConfig(
                projectId,
                request == null || request.markers() == null ? new ArrayList<>() : request.markers(),
                request == null || request.meta() == null ? new java.util.HashMap<>() : request.meta());
        List<GanttProjectConfig> all = new ArrayList<>(loadGanttConfigs());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (projectId.equals(all.get(i).projectId())) {
                all.set(i, newConfig);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(newConfig);
        saveGanttConfigs(all);
        return newConfig;
    }

    private List<GanttProjectConfig> loadGanttConfigs() throws IOException {
        return jsonStore.readList(ganttConfigsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveGanttConfigs(List<GanttProjectConfig> configs) throws IOException {
        jsonStore.writeList(ganttConfigsPath, configs);
    }

    private void ensureDataFiles() {
        try {
            ensureFile(profilesPath);
            ensureFile(budgetLinesPath);
            ensureFile(businessEpicsPath);
            ensureFile(budgetAllocationsPath);
            ensureFile(budgetLineAdjustmentsPath);
            ensureFile(globalRisksPath);
            ensureFile(peoplePath);
            ensureFile(monthlyHoursPath);
            ensureFile(consultanciesPath);
            ensureFile(focalPointsPath);
            ensureFile(absencesPath);
            ensureFile(incidentsPath);
            ensureFile(technicalDebtsPath);
            ensureFile(indicatorsPath);
            ensureFile(allocationPaymentsPath);
            ensureFile(loPresencePath);
            ensureFile(allocationMonthlyStatePath);
            ensureFile(allocationCursorsPath);
            ensureFile(ganttConfigsPath);
            ensureFileObject(feriadosPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de dados.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    private void ensureFileObject(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, "{}", StandardCharsets.UTF_8);
    }

    private void validatePersonRequest(CreatePersonRequest request) {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome da pessoa e obrigatorio.");
        }
        if (request.perfilId() == null || request.perfilId().isBlank()) {
            throw new IllegalArgumentException("Perfil da pessoa e obrigatorio.");
        }
        if (request.tipoVinculo() == null || request.tipoVinculo().isBlank()) {
            throw new IllegalArgumentException("Tipo de vinculo e obrigatorio.");
        }
        String tipo = request.tipoVinculo().trim().toUpperCase();
        if (!tipo.equals("BV") && !tipo.equals("TERCEIRO")) {
            throw new IllegalArgumentException("Tipo de vinculo deve ser BV ou TERCEIRO.");
        }
        Profile profile;
        try {
            profile = loadProfiles().stream()
                    .filter(p -> p.id().equals(request.perfilId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Perfil da pessoa nao encontrado."));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao validar perfil da pessoa.", ex);
        }
        boolean debitaLo = profile.debitaLo();
        if (tipo.equals("TERCEIRO")) {
            if (request.consultoria() == null || request.consultoria().isBlank()) {
                throw new IllegalArgumentException("Prestador de servico e obrigatorio para terceiros.");
            }
            if (debitaLo && (request.valorHora() == null || request.valorHora().compareTo(BigDecimal.ZERO) <= 0)) {
                throw new IllegalArgumentException("Valor hora e obrigatorio para terceiros.");
            }
            boolean prestadorExiste = false;
            try {
                String nome = request.consultoria().trim();
                prestadorExiste = loadConsultancies().stream().anyMatch(c -> c.nome().equalsIgnoreCase(nome));
            } catch (IOException ex) {
                throw new IllegalStateException("Falha ao validar prestador de servico.", ex);
            }
            if (!prestadorExiste) {
                throw new IllegalArgumentException("Prestador de servico nao encontrado. Cadastre antes de continuar.");
            }
        }
        // Para BV, valor mensal nao e obrigatorio: quando ausente, assume valor do perfil.
    }

    private BigDecimal resolveValorHora(CreatePersonRequest request, Profile profile) throws IOException {
        if (!profile.debitaLo()) {
            return profile.valorHora();
        }
        String tipo = request.tipoVinculo().trim().toUpperCase();
        if (tipo.equals("TERCEIRO")) return request.valorHora();
        if (request.valorMensal() == null || request.valorMensal().compareTo(BigDecimal.ZERO) <= 0) {
            return profile.valorHora();
        }
        int mesAtual = LocalDate.now().getMonthValue();
        BigDecimal horasMes = loadMonthlyHours().stream()
                .filter(h -> h.mes() == mesAtual)
                .map(MonthlyHours::horas)
                .findFirst()
                .orElse(BigDecimal.valueOf(160));
        if (horasMes.compareTo(BigDecimal.ZERO) <= 0) horasMes = BigDecimal.valueOf(160);
        return request.valorMensal().divide(horasMes, 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal resolveValorMensal(CreatePersonRequest request, Profile profile, BigDecimal valorHoraCalculado) {
        if (!profile.debitaLo()) return null;
        if (request.valorMensal() != null && request.valorMensal().compareTo(BigDecimal.ZERO) > 0) {
            return request.valorMensal();
        }
        BigDecimal valorHoraBase = valorHoraCalculado != null ? valorHoraCalculado : profile.valorHora();
        return valorHoraBase.multiply(BigDecimal.valueOf(160));
    }

    private BigDecimal resolveValorHoraPessoa(String nomePessoa, Profile profile) throws IOException {
        if (!profile.debitaLo()) return BigDecimal.ZERO;
        String nome = normalized(nomePessoa);
        if (nome.isBlank()) return profile.valorHora();
        return loadPeople().stream()
                .filter(p -> normalized(p.nome()).equals(nome))
                .filter(p -> p.perfilId() != null && p.perfilId().equals(profile.id()))
                .map(Person::valorHora)
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(profile.valorHora());
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String[] splitCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
                continue;
            }
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
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        String raw = stripQuotes(value);
        if (raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static OffsetDateTime parseDate(String value) {
        String raw = stripQuotes(value);
        if (raw.isBlank()) return null;
        java.time.LocalDate d = java.time.LocalDate.parse(raw);
        return d.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        String raw = stripQuotes(value).toLowerCase(Locale.ROOT);
        if (raw.isBlank()) return fallback;
        if (raw.equals("1") || raw.equals("true") || raw.equals("sim") || raw.equals("s")) return true;
        if (raw.equals("0") || raw.equals("false") || raw.equals("nao") || raw.equals("n")) return false;
        return fallback;
    }

    private Consultancy normalizeConsultancy(Consultancy consultancy) {
        if (consultancy == null) {
            return null;
        }
        String telefone = consultancy.telefone();
        String email = consultancy.email();
        String legacyContato = consultancy.contato();

        if ((telefone == null || telefone.isBlank()) && legacyContato != null && !legacyContato.isBlank()) {
            if (legacyContato.contains("@")) {
                email = legacyContato.trim();
            } else {
                telefone = legacyContato.trim();
            }
        }

        return new Consultancy(
                consultancy.id(),
                consultancy.nome(),
                consultancy.descricao(),
                telefone == null ? "" : telefone.trim(),
                email == null ? "" : email.trim(),
                "",
                consultancy.criadoEm());
    }

    private String resolveTelefone(CreateConsultancyRequest request) {
        String telefone = request.telefone();
        if ((telefone == null || telefone.isBlank()) && request.contato() != null && !request.contato().isBlank() && !request.contato().contains("@")) {
            telefone = request.contato();
        }
        return telefone == null ? "" : telefone.trim();
    }

    private String resolveEmail(CreateConsultancyRequest request) {
        String email = request.email();
        if ((email == null || email.isBlank()) && request.contato() != null && !request.contato().isBlank() && request.contato().contains("@")) {
            email = request.contato();
        }
        return email == null ? "" : email.trim();
    }

    private void validateBudgetLineAdjustmentRequest(CreateBudgetLineAdjustmentRequest request) {
        if (request == null || request.budgetLineId() == null || request.budgetLineId().isBlank()) {
            throw new IllegalArgumentException("LO da movimentacao e obrigatoria.");
        }
        if (request.tipo() == null || request.tipo().isBlank()) {
            throw new IllegalArgumentException("Tipo da movimentacao e obrigatorio.");
        }
        String tipo = request.tipo().trim().toUpperCase();
        if (!tipo.equals("TASK") && !tipo.equals("APORTE")) {
            throw new IllegalArgumentException("Tipo deve ser TASK ou APORTE.");
        }
        if (request.valor() == null || request.valor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor da movimentacao deve ser maior que zero.");
        }
    }

    private int sanitizeAno(Integer anoRequest) {
        int anoAtual = LocalDate.now().getYear();
        int ano = anoRequest == null ? anoAtual : anoRequest;
        if (ano < 2000 || ano > 2100) {
            throw new IllegalArgumentException("Ano da LO invalido. Use entre 2000 e 2100.");
        }
        return ano;
    }

    private void validateFocalPointRequest(CreateFocalPointRequest request) {
        if (request == null || request.area() == null || request.area().isBlank()) {
            throw new IllegalArgumentException("Area do ponto focal e obrigatoria.");
        }
        if (request.responsavelPor() == null || request.responsavelPor().isBlank()) {
            throw new IllegalArgumentException("Responsavel por e obrigatorio.");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Email do ponto focal e obrigatorio.");
        }
        if (request.telefone() == null || request.telefone().isBlank()) {
            throw new IllegalArgumentException("Telefone do ponto focal e obrigatorio.");
        }
    }

    @FunctionalInterface
    private interface Updater {
        ProjectRecord apply(ProjectRecord project);
    }
}
