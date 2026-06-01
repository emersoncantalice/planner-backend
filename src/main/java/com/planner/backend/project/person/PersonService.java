package com.planner.backend.project.person;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import com.planner.backend.application.port.ProjectStorePort;
import com.planner.backend.project.profile.ProfileService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PersonService {
    private static final Logger log = LoggerFactory.getLogger(PersonService.class);

    private final FileJsonStore jsonStore;
    private final ProfileService profileService;
    private final ProjectStorePort projectStorePort;
    private final java.nio.file.Path peoplePath;
    private final java.nio.file.Path absencesPath;
    private final java.nio.file.Path budgetAllocationsPath;
    private final java.nio.file.Path allocationPaymentsPath;
    private final java.nio.file.Path allocationMonthlyStatePath;
    private final java.nio.file.Path allocationPercentPath;
    private final java.nio.file.Path monthlyHoursPath;
    private final java.nio.file.Path consultanciesPath;

    public PersonService(
            FileJsonStore jsonStore,
            ProfileService profileService,
            ProjectStorePort projectStorePort,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.profileService = profileService;
        this.projectStorePort = projectStorePort;
        this.peoplePath                = java.nio.file.Path.of(dataDir, "people.json");
        this.absencesPath              = java.nio.file.Path.of(dataDir, "absences.json");
        this.budgetAllocationsPath     = java.nio.file.Path.of(dataDir, "budget-allocations.json");
        this.allocationPaymentsPath    = java.nio.file.Path.of(dataDir, "allocation-payments.json");
        this.allocationMonthlyStatePath= java.nio.file.Path.of(dataDir, "allocation-monthly-state.json");
        this.allocationPercentPath     = java.nio.file.Path.of(dataDir, "allocation-percent.json");
        this.monthlyHoursPath          = java.nio.file.Path.of(dataDir, "monthly-hours.json");
        this.consultanciesPath         = java.nio.file.Path.of(dataDir, "consultancies.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(peoplePath);
            ensureFile(absencesPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de pessoas.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    // ── People ────────────────────────────────────────────────────────────────

    public List<Person> listPeople() throws IOException {
        return loadPeople().stream().map(this::normalizePerson).toList();
    }

    public Person createPerson(CreatePersonRequest request) throws IOException {
        validatePersonRequest(request);
        Profile profile = profileService.loadProfiles().stream()
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
        Profile profile = profileService.loadProfiles().stream()
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
        Person deleted = all.stream()
                .filter(p -> p.id().equals(personId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pessoa nao encontrada."));
        all.remove(deleted);
        savePeople(all);

        // Clear responsavel from schedule items in projects
        String nome = deleted.nome();
        List<ProjectRecord> projects = new ArrayList<>(projectStorePort.loadProjects());
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
                        proj.riscos(), safeList(proj.historicoReplanejamento()),
                        proj.situacao(), proj.donoProjeto()));
                projChanged = true;
            }
        }
        if (projChanged) projectStorePort.saveProjects(projects);

        // Remove budget allocations associated with this person (by name)
        List<BudgetAllocation> budgetAllocs = new ArrayList<>(loadBudgetAllocations());
        Set<String> removedAllocIds = budgetAllocs.stream()
                .filter(a -> nome != null && nome.equals(a.nomePessoa()))
                .map(BudgetAllocation::id)
                .collect(Collectors.toSet());
        boolean allocChanged = budgetAllocs.removeIf(a -> nome != null && nome.equals(a.nomePessoa()));
        if (allocChanged) saveBudgetAllocations(budgetAllocs);

        if (!removedAllocIds.isEmpty()) {
            List<AllocationPaymentState> payments = new ArrayList<>(loadAllocationPayments());
            if (payments.removeIf(p -> removedAllocIds.contains(p.allocationId()))) saveAllocationPayments(payments);
            List<AllocationMonthlyState> monthly = new ArrayList<>(loadAllocationMonthlyStates());
            if (monthly.removeIf(m -> removedAllocIds.contains(m.allocationId()))) saveAllocationMonthlyStates(monthly);
            List<AllocationPercentConfig> pcts = new ArrayList<>(loadAllocationPercents());
            if (pcts.removeIf(p -> removedAllocIds.contains(p.allocationId()))) saveAllocationPercents(pcts);
        }

        // Remove absences
        List<Absence> absences = new ArrayList<>(loadAbsences());
        boolean absChanged = absences.removeIf(a -> personId.equals(a.pessoaId()));
        if (absChanged) saveAbsences(absences);
    }

    public ImportPeopleCsvResponse importPeopleCsv(ImportPeopleCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank()) {
            throw new IllegalArgumentException("CSV e obrigatorio.");
        }
        List<Profile> profiles = profileService.loadProfiles();
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("nome")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 4) { ignorados++; continue; }
            String nome        = stripQuotes(cols[0]);
            String perfilNome  = stripQuotes(cols[1]);
            String tipoVinculo = stripQuotes(cols[2]);
            String consultoria = cols.length > 3 ? stripQuotes(cols[3]) : "";
            BigDecimal valorHora   = parseDecimal(cols.length > 4 ? cols[4] : "");
            BigDecimal valorMensal = parseDecimal(cols.length > 5 ? cols[5] : "");
            Profile profile = profiles.stream()
                    .filter(p -> p.nomePerfil() != null && p.nomePerfil().equalsIgnoreCase(perfilNome))
                    .findFirst().orElse(null);
            if (profile == null) { ignorados++; continue; }
            String vagaUrl   = cols.length > 6 ? stripQuotes(cols[6]) : null;
            String vagaAlias = cols.length > 7 ? stripQuotes(cols[7]) : null;
            try {
                createPerson(new CreatePersonRequest(nome, profile.id(), tipoVinculo, consultoria,
                        valorHora, valorMensal, vagaUrl, vagaAlias, null, null, true, null));
                criados++;
            } catch (IllegalArgumentException ex) {
                ignorados++;
            }
        }
        return new ImportPeopleCsvResponse(criados, ignorados);
    }

    // ── Absences ──────────────────────────────────────────────────────────────

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
        log.info("Ausencia criada: id={}, pessoa={}, tipo={}", absence.id(), absence.pessoaNome(), absence.tipo());
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

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<Person> loadPeople() throws IOException {
        return jsonStore.readList(peoplePath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public void savePeople(List<Person> people) throws IOException {
        jsonStore.writeList(peoplePath, people);
    }

    private List<Absence> loadAbsences() throws IOException {
        return jsonStore.readList(absencesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAbsences(List<Absence> absences) throws IOException {
        jsonStore.writeList(absencesPath, absences);
    }

    private List<BudgetAllocation> loadBudgetAllocations() throws IOException {
        return jsonStore.readList(budgetAllocationsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveBudgetAllocations(List<BudgetAllocation> list) throws IOException {
        jsonStore.writeList(budgetAllocationsPath, list);
    }

    private List<AllocationPaymentState> loadAllocationPayments() throws IOException {
        return jsonStore.readList(allocationPaymentsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveAllocationPayments(List<AllocationPaymentState> list) throws IOException {
        jsonStore.writeList(allocationPaymentsPath, list);
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

    private List<MonthlyHours> loadMonthlyHours() throws IOException {
        return jsonStore.readList(monthlyHoursPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private List<Consultancy> loadConsultancies() throws IOException {
        return jsonStore.readList(consultanciesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private void validatePersonRequest(CreatePersonRequest request) {
        if (request == null || request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome da pessoa e obrigatorio.");
        if (request.perfilId() == null || request.perfilId().isBlank())
            throw new IllegalArgumentException("Perfil da pessoa e obrigatorio.");
        if (request.tipoVinculo() == null || request.tipoVinculo().isBlank())
            throw new IllegalArgumentException("Tipo de vinculo e obrigatorio.");
        String tipo = request.tipoVinculo().trim().toUpperCase();
        if (!tipo.equals("BV") && !tipo.equals("TERCEIRO"))
            throw new IllegalArgumentException("Tipo de vinculo deve ser BV ou TERCEIRO.");
        Profile profile;
        try {
            profile = profileService.loadProfiles().stream()
                    .filter(p -> p.id().equals(request.perfilId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Perfil da pessoa nao encontrado."));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao validar perfil da pessoa.", ex);
        }
        boolean debitaLo = profile.debitaLo();
        if (tipo.equals("TERCEIRO")) {
            if (request.consultoria() == null || request.consultoria().isBlank())
                throw new IllegalArgumentException("Prestador de servico e obrigatorio para terceiros.");
            if (debitaLo && (request.valorHora() == null || request.valorHora().compareTo(BigDecimal.ZERO) <= 0))
                throw new IllegalArgumentException("Valor hora e obrigatorio para terceiros.");
            boolean prestadorExiste;
            try {
                String nomeConsultoria = request.consultoria().trim();
                prestadorExiste = loadConsultancies().stream().anyMatch(c -> c.nome().equalsIgnoreCase(nomeConsultoria));
            } catch (IOException ex) {
                throw new IllegalStateException("Falha ao validar prestador de servico.", ex);
            }
            if (!prestadorExiste)
                throw new IllegalArgumentException("Prestador de servico nao encontrado. Cadastre antes de continuar.");
        }
    }

    private BigDecimal resolveValorHora(CreatePersonRequest request, Profile profile) throws IOException {
        if (!profile.debitaLo()) return profile.valorHora();
        String tipo = request.tipoVinculo().trim().toUpperCase();
        if (tipo.equals("TERCEIRO")) return request.valorHora();
        // For BV/Folha: prefer explicit valorHora if provided
        if (request.valorHora() != null && request.valorHora().compareTo(BigDecimal.ZERO) > 0)
            return request.valorHora();
        if (request.valorMensal() == null || request.valorMensal().compareTo(BigDecimal.ZERO) <= 0)
            return profile.valorHora();
        int mesAtual = LocalDate.now().getMonthValue();
        BigDecimal horasMes = loadMonthlyHours().stream()
                .filter(h -> h.mes() == mesAtual)
                .map(MonthlyHours::horas)
                .findFirst()
                .orElse(BigDecimal.valueOf(160));
        if (horasMes.compareTo(BigDecimal.ZERO) <= 0) horasMes = BigDecimal.valueOf(160);
        return request.valorMensal().divide(horasMes, 3, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal resolveValorMensal(CreatePersonRequest request, Profile profile, BigDecimal valorHoraCalculado) {
        if (!profile.debitaLo()) return null;
        if (request.valorMensal() != null && request.valorMensal().compareTo(BigDecimal.ZERO) > 0)
            return request.valorMensal();
        BigDecimal valorHoraBase = valorHoraCalculado != null ? valorHoraCalculado : profile.valorHora();
        return valorHoraBase.multiply(BigDecimal.valueOf(160));
    }

    private Person normalizePerson(Person p) {
        if (p.ativo() != null) return p;
        return new Person(p.id(), p.nome(), p.perfilId(), p.perfilNome(),
                p.tipoVinculo(), p.consultoria(), p.valorHora(), p.valorMensal(),
                p.vagaUrl(), p.vagaAlias(), p.dataNascimento(), p.contato(),
                true,
                p.vagasAnteriores() != null ? p.vagasAnteriores() : java.util.List.of(),
                p.criadoEm());
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? new ArrayList<>() : list;
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

    private static BigDecimal parseDecimal(String value) {
        String raw = stripQuotes(value);
        if (raw.isBlank()) return null;
        String normalized = raw.replace("R$", "").replace(" ", "");
        if (normalized.contains(",") && normalized.contains("."))
            normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.contains(","))
            normalized = normalized.replace(",", ".");
        try { return new BigDecimal(normalized); } catch (NumberFormatException ex) { return null; }
    }
}
