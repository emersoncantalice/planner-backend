package com.planner.backend.project.budget;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
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
public class BudgetLineService {
    private static final Logger log = LoggerFactory.getLogger(BudgetLineService.class);

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path budgetLinesPath;
    private final java.nio.file.Path budgetLineAdjustmentsPath;
    private final java.nio.file.Path businessEpicsPath;
    private final java.nio.file.Path budgetAllocationsPath;
    private final java.nio.file.Path allocationPaymentsPath;
    private final java.nio.file.Path allocationMonthlyStatePath;
    private final java.nio.file.Path allocationPercentPath;
    private final java.nio.file.Path loRealizadoPath;

    public BudgetLineService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.budgetLinesPath             = java.nio.file.Path.of(dataDir, "budget-lines.json");
        this.budgetLineAdjustmentsPath   = java.nio.file.Path.of(dataDir, "budget-line-adjustments.json");
        this.businessEpicsPath           = java.nio.file.Path.of(dataDir, "business-epics.json");
        this.budgetAllocationsPath       = java.nio.file.Path.of(dataDir, "budget-allocations.json");
        this.allocationPaymentsPath      = java.nio.file.Path.of(dataDir, "allocation-payments.json");
        this.allocationMonthlyStatePath  = java.nio.file.Path.of(dataDir, "allocation-monthly-state.json");
        this.allocationPercentPath       = java.nio.file.Path.of(dataDir, "allocation-percent.json");
        this.loRealizadoPath             = java.nio.file.Path.of(dataDir, "lo-realizado.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            ensureFile(budgetLinesPath);
            ensureFile(budgetLineAdjustmentsPath);
            ensureFile(businessEpicsPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivos de budget-lines.", ex);
        }
    }

    private void ensureFile(java.nio.file.Path path) throws IOException {
        if (Files.exists(path)) return;
        java.nio.file.Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, "[]", StandardCharsets.UTF_8);
    }

    // ── Budget Lines ──────────────────────────────────────────────────────────

    public List<BudgetLine> listBudgetLines(String username, String role) throws IOException {
        List<BudgetLine> all = loadBudgetLines();
        if ("ADMIN".equals(role)) {
            return all.stream()
                    .filter(lo -> !"DRAFT".equals(lo.situacao())
                            || lo.dono() == null
                            || username.equalsIgnoreCase(lo.dono()))
                    .toList();
        }
        return all.stream()
                .filter(lo -> lo.dono() == null
                        || username.equalsIgnoreCase(lo.dono())
                        || !"DRAFT".equals(lo.situacao()))
                .toList();
    }

    public List<BudgetLine> listBudgetLines() throws IOException {
        return loadBudgetLines();
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

    public BudgetLine updateBudgetLineSituacao(String id, String situacao, String username, String role) throws IOException {
        if (!"DRAFT".equals(situacao) && !"PUBLISHED".equals(situacao))
            throw new IllegalArgumentException("Situacao invalida. Use DRAFT ou PUBLISHED.");
        List<BudgetLine> all = new ArrayList<>(loadBudgetLines());
        for (int i = 0; i < all.size(); i++) {
            BudgetLine lo = all.get(i);
            if (lo.id().equals(id)) {
                boolean isOwner = username.equalsIgnoreCase(lo.dono()) || lo.dono() == null;
                if (!isOwner && !"ADMIN".equals(role))
                    throw new IllegalArgumentException("Sem permissao para alterar a situacao desta LO.");
                BudgetLine updated = new BudgetLine(lo.id(), lo.codigo(), lo.nome(), lo.ano(),
                        lo.tipo(), lo.centroCusto(), lo.valorTotal(), lo.criadoEm(), situacao, lo.dono());
                all.set(i, updated);
                saveBudgetLines(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("LO nao encontrada.");
    }

    public ImportCsvResponse importBudgetLinesCsv(ImportBudgetLinesCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank())
            throw new IllegalArgumentException("CSV e obrigatorio.");
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("codigo")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 6) { ignorados++; continue; }
            try {
                createBudgetLine(new CreateBudgetLineRequest(
                        stripQuotes(cols[0]), stripQuotes(cols[1]),
                        parseInteger(cols[2]), stripQuotes(cols[3]),
                        stripQuotes(cols[4]), parseDecimal(cols[5])));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public BudgetLine updateBudgetLine(String budgetLineId, CreateBudgetLineRequest request) throws IOException {
        if (request == null || request.codigo() == null || request.codigo().isBlank())
            throw new IllegalArgumentException("Codigo da LO e obrigatorio.");
        if (request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome da LO e obrigatorio.");
        if (request.valorTotal() == null || request.valorTotal().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Valor total da LO deve ser maior que zero.");
        int ano = sanitizeAno(request.ano());
        List<BudgetLine> all = new ArrayList<>(loadBudgetLines());
        for (int i = 0; i < all.size(); i++) {
            BudgetLine lo = all.get(i);
            if (lo.id().equals(budgetLineId)) {
                BudgetLine updated = new BudgetLine(
                        lo.id(), request.codigo().trim(), request.nome().trim(), ano,
                        request.tipo() == null || request.tipo().isBlank() ? "RUN" : request.tipo().trim().toUpperCase(),
                        request.centroCusto() == null ? "" : request.centroCusto().trim(),
                        request.valorTotal(), lo.criadoEm(), lo.situacao(), lo.dono());
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

        List<BudgetAllocation> allocations = new ArrayList<>(loadBudgetAllocations());
        Set<String> removedAllocIds = allocations.stream()
                .filter(a -> budgetLineId.equals(a.linhaOrcamentariaId()))
                .map(BudgetAllocation::id)
                .collect(Collectors.toSet());
        allocations.removeIf(a -> budgetLineId.equals(a.linhaOrcamentariaId()));
        saveBudgetAllocations(allocations);

        List<BudgetLineAdjustment> adjustments = new ArrayList<>(loadBudgetLineAdjustments());
        adjustments.removeIf(a -> budgetLineId.equals(a.budgetLineId()));
        saveBudgetLineAdjustments(adjustments);

        if (!removedAllocIds.isEmpty()) {
            List<AllocationPaymentState> payments = new ArrayList<>(loadAllocationPayments());
            if (payments.removeIf(p -> removedAllocIds.contains(p.allocationId()))) saveAllocationPayments(payments);
            List<AllocationMonthlyState> monthly = new ArrayList<>(loadAllocationMonthlyStates());
            if (monthly.removeIf(m -> removedAllocIds.contains(m.allocationId()))) saveAllocationMonthlyStates(monthly);
            List<AllocationPercentConfig> pcts = new ArrayList<>(loadAllocationPercents());
            if (pcts.removeIf(p -> removedAllocIds.contains(p.allocationId()))) saveAllocationPercents(pcts);
        }
        List<LoRealizadoConfig> realizados = new ArrayList<>(loadLoRealizado());
        if (realizados.removeIf(r -> budgetLineId.equals(r.loId()))) saveLoRealizado(realizados);
    }

    public BudgetLine transferBudgetLineDono(String id, String novoDono, String username, String role) throws IOException {
        if (novoDono == null || novoDono.isBlank()) throw new IllegalArgumentException("Novo dono e obrigatorio.");
        List<BudgetLine> all = new ArrayList<>(loadBudgetLines());
        for (int i = 0; i < all.size(); i++) {
            BudgetLine lo = all.get(i);
            if (lo.id().equals(id)) {
                boolean isOwner = lo.dono() == null || username.equalsIgnoreCase(lo.dono());
                if (!isOwner && !"ADMIN".equals(role))
                    throw new IllegalArgumentException("Sem permissao para transferir esta LO.");
                BudgetLine updated = new BudgetLine(lo.id(), lo.codigo(), lo.nome(), lo.ano(),
                        lo.tipo(), lo.centroCusto(), lo.valorTotal(), lo.criadoEm(), lo.situacao(), novoDono.trim());
                all.set(i, updated);
                saveBudgetLines(all);
                return updated;
            }
        }
        throw new IllegalArgumentException("LO nao encontrada.");
    }

    // ── Budget Line Adjustments ───────────────────────────────────────────────

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
                UUID.randomUUID().toString(), lo.id(),
                request.tipo().trim().toUpperCase(),
                request.descricao() == null ? "" : request.descricao().trim(),
                request.valor(), OffsetDateTime.now());
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
                        current.id(), lo.id(), request.tipo().trim().toUpperCase(),
                        request.descricao() == null ? "" : request.descricao().trim(),
                        request.valor(), current.criadoEm());
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

    // ── Business Epics ────────────────────────────────────────────────────────

    public List<BusinessEpic> listBusinessEpics() throws IOException {
        return loadBusinessEpics();
    }

    public BusinessEpic createBusinessEpic(CreateBusinessEpicRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome do Business Epic e obrigatorio.");
        if (request.jiraUrl() == null || request.jiraUrl().isBlank())
            throw new IllegalArgumentException("Link Jira do Business Epic e obrigatorio.");
        if (request.inicio() == null || request.fim() == null)
            throw new IllegalArgumentException("Datas de inicio e fim do Business Epic sao obrigatorias.");
        if (request.fim().isBefore(request.inicio()))
            throw new IllegalArgumentException("Data fim do Business Epic nao pode ser anterior ao inicio.");
        BusinessEpic created = new BusinessEpic(
                UUID.randomUUID().toString(),
                request.nome().trim(),
                request.aliasLink() == null ? "" : request.aliasLink().trim(),
                request.jiraUrl().trim(),
                request.inicio(), request.fim(),
                OffsetDateTime.now());
        List<BusinessEpic> all = new ArrayList<>(loadBusinessEpics());
        all.add(created);
        saveBusinessEpics(all);
        log.info("Business Epic criado: id={}, nome={}", created.id(), created.nome());
        return created;
    }

    public ImportCsvResponse importBusinessEpicsCsv(ImportBusinessEpicsCsvRequest request) throws IOException {
        if (request == null || request.csv() == null || request.csv().isBlank())
            throw new IllegalArgumentException("CSV e obrigatorio.");
        int criados = 0;
        int ignorados = 0;
        String[] lines = request.csv().replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.toLowerCase(Locale.ROOT).contains("jira")) continue;
            String[] cols = splitCsvLine(line);
            if (cols.length < 4) { ignorados++; continue; }
            try {
                String nome  = stripQuotes(cols[0]);
                String alias = cols.length >= 5 ? stripQuotes(cols[1]) : "";
                String jira  = cols.length >= 5 ? stripQuotes(cols[2]) : stripQuotes(cols[1]);
                String inicio = cols.length >= 5 ? cols[3] : cols[2];
                String fim    = cols.length >= 5 ? cols[4] : cols[3];
                createBusinessEpic(new CreateBusinessEpicRequest(nome, alias, jira, parseDate(inicio), parseDate(fim)));
                criados++;
            } catch (Exception ex) {
                ignorados++;
            }
        }
        return new ImportCsvResponse(criados, ignorados);
    }

    public BusinessEpic updateBusinessEpic(String epicId, CreateBusinessEpicRequest request) throws IOException {
        if (request == null || request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome do Business Epic e obrigatorio.");
        if (request.jiraUrl() == null || request.jiraUrl().isBlank())
            throw new IllegalArgumentException("Link Jira do Business Epic e obrigatorio.");
        if (request.inicio() == null || request.fim() == null)
            throw new IllegalArgumentException("Datas de inicio e fim do Business Epic sao obrigatorias.");
        if (request.fim().isBefore(request.inicio()))
            throw new IllegalArgumentException("Data fim do Business Epic nao pode ser anterior ao inicio.");
        List<BusinessEpic> all = new ArrayList<>(loadBusinessEpics());
        for (int i = 0; i < all.size(); i++) {
            BusinessEpic e = all.get(i);
            if (e.id().equals(epicId)) {
                BusinessEpic updated = new BusinessEpic(
                        e.id(), request.nome().trim(),
                        request.aliasLink() == null ? "" : request.aliasLink().trim(),
                        request.jiraUrl().trim(), request.inicio(), request.fim(), e.criadoEm());
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

    // ── I/O helpers ───────────────────────────────────────────────────────────

    public List<BudgetLine> loadBudgetLines() throws IOException {
        List<BudgetLine> all = jsonStore.readList(budgetLinesPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        int anoAtual = LocalDate.now().getYear();
        boolean changed = false;
        List<BudgetLine> normalized = new ArrayList<>(all.size());
        for (BudgetLine lo : all) {
            int ano = lo.ano() <= 0 ? anoAtual : lo.ano();
            if (ano != lo.ano()) changed = true;
            normalized.add(new BudgetLine(lo.id(), lo.codigo(), lo.nome(), ano, lo.tipo(),
                    lo.centroCusto(), lo.valorTotal(), lo.criadoEm(), lo.situacao(), lo.dono()));
        }
        if (changed) saveBudgetLines(normalized);
        return normalized;
    }

    public void saveBudgetLines(List<BudgetLine> budgetLines) throws IOException {
        jsonStore.writeList(budgetLinesPath, budgetLines);
    }

    private List<BudgetLineAdjustment> loadBudgetLineAdjustments() throws IOException {
        return jsonStore.readList(budgetLineAdjustmentsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveBudgetLineAdjustments(List<BudgetLineAdjustment> list) throws IOException {
        jsonStore.writeList(budgetLineAdjustmentsPath, list);
    }

    private List<BusinessEpic> loadBusinessEpics() throws IOException {
        return jsonStore.readList(businessEpicsPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveBusinessEpics(List<BusinessEpic> list) throws IOException {
        jsonStore.writeList(businessEpicsPath, list);
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

    private List<LoRealizadoConfig> loadLoRealizado() throws IOException {
        return jsonStore.readList(loRealizadoPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveLoRealizado(List<LoRealizadoConfig> list) throws IOException {
        jsonStore.writeList(loRealizadoPath, list);
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private void validateBudgetLineAdjustmentRequest(CreateBudgetLineAdjustmentRequest request) {
        if (request == null || request.budgetLineId() == null || request.budgetLineId().isBlank())
            throw new IllegalArgumentException("LO da movimentacao e obrigatoria.");
        if (request.tipo() == null || request.tipo().isBlank())
            throw new IllegalArgumentException("Tipo da movimentacao e obrigatorio.");
        String tipo = request.tipo().trim().toUpperCase();
        if (!tipo.equals("TASK") && !tipo.equals("APORTE"))
            throw new IllegalArgumentException("Tipo deve ser TASK ou APORTE.");
        if (request.valor() == null || request.valor().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Valor da movimentacao deve ser maior que zero.");
    }

    private int sanitizeAno(Integer anoRequest) {
        int anoAtual = LocalDate.now().getYear();
        int ano = anoRequest == null ? anoAtual : anoRequest;
        if (ano < 2000 || ano > 2100)
            throw new IllegalArgumentException("Ano da LO invalido. Use entre 2000 e 2100.");
        return ano;
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

    private static Integer parseInteger(String value) {
        String raw = stripQuotes(value);
        if (raw.isBlank()) return null;
        try { return Integer.parseInt(raw); } catch (NumberFormatException ex) { return null; }
    }

    private static OffsetDateTime parseDate(String value) {
        String raw = stripQuotes(value);
        if (raw.isBlank()) return null;
        java.time.LocalDate d = java.time.LocalDate.parse(raw);
        return d.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
    }
}
