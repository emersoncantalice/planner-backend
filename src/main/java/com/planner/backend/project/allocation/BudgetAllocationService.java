package com.planner.backend.project.allocation;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import com.planner.backend.project.budget.BudgetLineService;
import com.planner.backend.project.person.PersonService;
import com.planner.backend.project.profile.ProfileService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BudgetAllocationService {

    private final FileJsonStore jsonStore;
    private final ProfileService profileService;
    private final PersonService personService;
    private final BudgetLineService budgetLineService;
    private final java.nio.file.Path budgetAllocationsPath;
    private final java.nio.file.Path allocationPaymentsPath;
    private final java.nio.file.Path allocationMonthlyStatePath;
    private final java.nio.file.Path allocationPercentPath;

    public BudgetAllocationService(
            FileJsonStore jsonStore,
            ProfileService profileService,
            PersonService personService,
            BudgetLineService budgetLineService,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.profileService = profileService;
        this.personService = personService;
        this.budgetLineService = budgetLineService;
        this.budgetAllocationsPath    = java.nio.file.Path.of(dataDir, "budget-allocations.json");
        this.allocationPaymentsPath   = java.nio.file.Path.of(dataDir, "allocation-payments.json");
        this.allocationMonthlyStatePath = java.nio.file.Path.of(dataDir, "allocation-monthly-state.json");
        this.allocationPercentPath    = java.nio.file.Path.of(dataDir, "allocation-percent.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(budgetAllocationsPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de budget-allocations.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<BudgetAllocation> listBudgetAllocations() throws IOException {
        return loadBudgetAllocations();
    }

    public BudgetAllocation createBudgetAllocation(CreateBudgetAllocationRequest request) throws IOException {
        if (request.linhaOrcamentariaId() == null || request.linhaOrcamentariaId().isBlank())
            throw new IllegalArgumentException("LO da alocacao e obrigatoria.");
        if (request.perfilId() == null || request.perfilId().isBlank())
            throw new IllegalArgumentException("Perfil e obrigatorio.");
        if (request.horasPlanejadas() <= 0)
            throw new IllegalArgumentException("Horas planejadas deve ser maior que zero.");

        boolean isDraft = Boolean.TRUE.equals(request.draft());

        BudgetLine lo = budgetLineService.loadBudgetLines().stream()
                .filter(b -> b.id().equals(request.linhaOrcamentariaId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("LO nao encontrada."));

        boolean loDraft = "DRAFT".equals(lo.situacao());
        if (loDraft && !isDraft)
            throw new IllegalArgumentException("Esta LO e um rascunho. So e possivel adicionar alocacoes do tipo rascunho.");

        if (request.nomePessoa() == null || request.nomePessoa().isBlank())
            throw new IllegalArgumentException("Nome da pessoa e obrigatorio.");

        Profile profile = profileService.loadProfiles().stream()
                .filter(p -> p.id().equals(request.perfilId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil nao encontrado."));

        List<BudgetAllocation> existingAll = loadBudgetAllocations();
        String nomePessoa = request.nomePessoa().trim();

        BigDecimal valorHoraAplicado = resolveValorHoraPessoa(nomePessoa, profile);
        BigDecimal custo = valorHoraAplicado.multiply(BigDecimal.valueOf(request.horasPlanejadas()));
        int mesInicio = request.mesInicio() != null ? Math.max(0, Math.min(11, request.mesInicio())) : 0;

        BudgetAllocation created = new BudgetAllocation(
                java.util.UUID.randomUUID().toString(),
                lo.id(), lo.codigo(), nomePessoa,
                profile.id(), profile.nomePerfil(),
                valorHoraAplicado, loDraft ? false : profile.debitaLo(),
                request.horasPlanejadas(), custo,
                OffsetDateTime.now(),
                isDraft ? true : null,
                mesInicio > 0 ? mesInicio : null);

        List<BudgetAllocation> all = new ArrayList<>(existingAll);
        all.add(created);
        saveBudgetAllocations(all);

        if (mesInicio > 0) autoApplyMesInicio(created.id(), mesInicio);

        return created;
    }

    public BudgetAllocation updateBudgetAllocation(String allocationId, CreateBudgetAllocationRequest request) throws IOException {
        if (request.linhaOrcamentariaId() == null || request.linhaOrcamentariaId().isBlank())
            throw new IllegalArgumentException("LO da alocacao e obrigatoria.");
        if (request.nomePessoa() == null || request.nomePessoa().isBlank())
            throw new IllegalArgumentException("Nome da pessoa e obrigatorio.");
        if (request.perfilId() == null || request.perfilId().isBlank())
            throw new IllegalArgumentException("Perfil e obrigatorio.");
        if (request.horasPlanejadas() <= 0)
            throw new IllegalArgumentException("Horas planejadas deve ser maior que zero.");

        boolean isDraft = Boolean.TRUE.equals(request.draft());

        BudgetLine lo = budgetLineService.loadBudgetLines().stream()
                .filter(b -> b.id().equals(request.linhaOrcamentariaId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("LO nao encontrada."));

        boolean loDraft = "DRAFT".equals(lo.situacao());
        if (loDraft && !isDraft)
            throw new IllegalArgumentException("Esta LO e um rascunho. So e possivel ter alocacoes do tipo rascunho.");

        Profile profile = profileService.loadProfiles().stream()
                .filter(p -> p.id().equals(request.perfilId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil nao encontrado."));

        List<BudgetAllocation> all = new ArrayList<>(loadBudgetAllocations());
        for (int i = 0; i < all.size(); i++) {
            BudgetAllocation a = all.get(i);
            if (!a.id().equals(allocationId)) continue;

            BigDecimal valorHoraAplicado = resolveValorHoraPessoa(request.nomePessoa(), profile);
            BigDecimal custo = valorHoraAplicado.multiply(BigDecimal.valueOf(request.horasPlanejadas()));
            int mesInicio = request.mesInicio() != null ? Math.max(0, Math.min(11, request.mesInicio())) : 0;

            BudgetAllocation updated = new BudgetAllocation(
                    a.id(), lo.id(), lo.codigo(), request.nomePessoa().trim(),
                    profile.id(), profile.nomePerfil(),
                    valorHoraAplicado, isDraft ? false : profile.debitaLo(),
                    request.horasPlanejadas(), custo, a.criadoEm(),
                    isDraft ? true : null,
                    mesInicio > 0 ? mesInicio : null);

            all.set(i, updated);
            saveBudgetAllocations(all);
            if (mesInicio > 0) autoApplyMesInicio(allocationId, mesInicio);
            return updated;
        }
        throw new IllegalArgumentException("Alocacao LO nao encontrada.");
    }

    public void deleteBudgetAllocation(String allocationId) throws IOException {
        List<BudgetAllocation> all = new ArrayList<>(loadBudgetAllocations());
        boolean removed = all.removeIf(a -> a.id().equals(allocationId));
        if (!removed) throw new IllegalArgumentException("Alocacao LO nao encontrada.");
        saveBudgetAllocations(all);

        List<AllocationPaymentState> payments = new ArrayList<>(loadAllocationPayments());
        if (payments.removeIf(p -> allocationId.equals(p.allocationId()))) saveAllocationPayments(payments);
        List<AllocationMonthlyState> monthly = new ArrayList<>(loadAllocationMonthlyStates());
        if (monthly.removeIf(m -> allocationId.equals(m.allocationId()))) saveAllocationMonthlyStates(monthly);
        List<AllocationPercentConfig> pcts = new ArrayList<>(loadAllocationPercents());
        if (pcts.removeIf(p -> allocationId.equals(p.allocationId()))) saveAllocationPercents(pcts);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private BigDecimal resolveValorHoraPessoa(String nomePessoa, Profile profile) throws IOException {
        if (!profile.debitaLo()) return BigDecimal.ZERO;
        String nome = normalized(nomePessoa);
        if (nome.isBlank()) return profile.valorHora();
        return personService.loadPeople().stream()
                .filter(p -> normalized(p.nome()).equals(nome))
                .filter(p -> p.perfilId() != null && p.perfilId().equals(profile.id()))
                .map(Person::valorHora)
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(profile.valorHora());
    }

    private void autoApplyMesInicio(String allocationId, int mesInicio) throws IOException {
        if (mesInicio <= 0) return;
        List<AllocationMonthlyState> all = new ArrayList<>(loadAllocationMonthlyStates());
        for (int m = 0; m < mesInicio; m++) {
            final int month = m;
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                AllocationMonthlyState s = all.get(i);
                if (allocationId.equals(s.allocationId()) && s.month() == month) {
                    all.set(i, new AllocationMonthlyState(allocationId, month, true, s.manualValue(), s.manualPercent(), OffsetDateTime.now(), "system"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                all.add(new AllocationMonthlyState(allocationId, month, true, null, null, OffsetDateTime.now(), "system"));
            }
        }
        saveAllocationMonthlyStates(all);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<BudgetAllocation> loadBudgetAllocations() throws IOException {
        return jsonStore.readList(budgetAllocationsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public void saveBudgetAllocations(List<BudgetAllocation> list) throws IOException {
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
}
