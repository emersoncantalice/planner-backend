package com.planner.backend.project;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class ProjectModels {
    private ProjectModels() {}

    public record Step(String id, String titulo, boolean concluido, OffsetDateTime concluidoEm) {}

    public record Profile(String id, String nomePerfil, BigDecimal valorHora, boolean debitaLo, OffsetDateTime criadoEm) {}

    public record Allocation(
            String id,
            String nomePessoa,
            String perfilId,
            String perfilNome,
            BigDecimal valorHora,
            boolean debitaLo,
            int horasPlanejadas,
            BigDecimal custoPlanejado,
            OffsetDateTime criadoEm) {}

    public record FinanceEntry(
            String id,
            String tipo,
            String descricao,
            BigDecimal valor,
            OffsetDateTime dataLancamento,
            OffsetDateTime criadoEm) {}

    public record ScheduleItem(
            String id,
            String titulo,
            String descricao,
            OffsetDateTime inicioPlanejado,
            OffsetDateTime fimPlanejado,
            boolean permiteParalelo,
            String status,
            int ordem,
            OffsetDateTime criadoEm,
            String cor,
            String responsavel) {}

    public record RiskItem(
            String id,
            String titulo,
            String descricao,
            OffsetDateTime dataFim,
            String status,
            OffsetDateTime criadoEm) {}

    public record ReplanningEvent(
            String id,
            String scheduleItemId,
            String scheduleItemTitulo,
            String tipoAlteracao,        
            OffsetDateTime inicioAnterior,
            OffsetDateTime fimAnterior,
            OffsetDateTime inicioNovo,
            OffsetDateTime fimNovo,
            OffsetDateTime registradoEm) {}

    public record ProjectRecord(
            String id,
            String nome,
            String descricao,
            OffsetDateTime criadoEm,
            List<Step> etapas,
            List<ScheduleItem> cronograma,
            List<Allocation> alocacoes,
            List<FinanceEntry> financeiro,
            List<RiskItem> riscos,
            List<ReplanningEvent> historicoReplanejamento,
            String situacao,      
            String donoProjeto) {}// username of creator — null means legacy/shared

    public record AddReplanningEventRequest(
            String scheduleItemId,
            String scheduleItemTitulo,
            String tipoAlteracao,
            OffsetDateTime inicioAnterior,
            OffsetDateTime fimAnterior,
            OffsetDateTime inicioNovo,
            OffsetDateTime fimNovo) {}

    public record CreateProjectRequest(String nome, String descricao) {}
    public record UpdateProjectSituacaoRequest(String situacao) {}

    public record CreateStepRequest(String titulo) {}

    public record ToggleStepRequest(boolean concluido) {}

    public record CreateAllocationRequest(String nomePessoa, String perfilId, int horasPlanejadas) {}

    public record CreateProfileRequest(String nomePerfil, BigDecimal valorHora, boolean debitaLo) {}
    public record ImportProfilesCsvRequest(String csv) {}

    public record BudgetLine(
            String id,
            String codigo,
            String nome,
            int ano,
            String tipo,
            String centroCusto,
            BigDecimal valorTotal,
            OffsetDateTime criadoEm,
            String situacao,  
            String dono) {}   

    public record CreateBudgetLineRequest(
            String codigo,
            String nome,
            Integer ano,
            String tipo,
            String centroCusto,
            BigDecimal valorTotal) {}
    public record ImportBudgetLinesCsvRequest(String csv) {}
    public record UpdateBudgetLineSituacaoRequest(String situacao) {}

    public record BudgetLineAdjustment(
            String id,
            String budgetLineId,
            String tipo,
            String descricao,
            BigDecimal valor,
            OffsetDateTime criadoEm) {}

    public record CreateBudgetLineAdjustmentRequest(
            String budgetLineId,
            String tipo,
            String descricao,
            BigDecimal valor) {}

    public record BusinessEpic(
            String id,
            String nome,
            String aliasLink,
            String jiraUrl,
            OffsetDateTime inicio,
            OffsetDateTime fim,
            OffsetDateTime criadoEm) {}

    public record CreateBusinessEpicRequest(
            String nome,
            String aliasLink,
            String jiraUrl,
            OffsetDateTime inicio,
            OffsetDateTime fim) {}
    public record ImportBusinessEpicsCsvRequest(String csv) {}

    public record BudgetAllocation(
            String id,
            String linhaOrcamentariaId,
            String linhaOrcamentariaCodigo,
            String nomePessoa,
            String perfilId,
            String perfilNome,
            BigDecimal valorHora,
            boolean debitaLo,
            int horasPlanejadas,
            BigDecimal custoPlanejado,
            OffsetDateTime criadoEm) {}

    public record CreateBudgetAllocationRequest(
            String linhaOrcamentariaId,
            String nomePessoa,
            String perfilId,
            int horasPlanejadas) {}

    public record CreateFinanceEntryRequest(String tipo, String descricao, BigDecimal valor, OffsetDateTime dataLancamento) {}

    public record CreateScheduleItemRequest(
            String titulo,
            String descricao,
            OffsetDateTime inicioPlanejado,
            OffsetDateTime fimPlanejado,
            boolean permiteParalelo,
            String cor,
            String responsavel) {}

    public record UpdateScheduleItemRequest(
            String titulo,
            String descricao,
            OffsetDateTime inicioPlanejado,
            OffsetDateTime fimPlanejado,
            boolean permiteParalelo,
            String status,
            String cor,
            String responsavel) {}

    public record ReorderScheduleRequest(List<String> itemIdsOrdered) {}

    public record CreateRiskItemRequest(
            String titulo,
            String descricao,
            OffsetDateTime dataFim) {}

    public record UpdateRiskItemStatusRequest(String status) {}

    public record GlobalRisk(
            String id,
            String titulo,
            String descricao,
            String planoAcao,
            OffsetDateTime dataFim,
            String status,
            String responsavel,
            List<GlobalRiskHistoryEvent> historico,
            OffsetDateTime criadoEm,
            String criadoPor) {}  

    public record CreateGlobalRiskRequest(
            String titulo,
            String descricao,
            String planoAcao,
            String status,
            OffsetDateTime dataFim,
            String responsavel) {}

    public record UpdateGlobalRiskStatusRequest(String status) {}

    public record ProjectActivity(
            String id,
            String titulo,
            String descricao,
            String projetoId,
            String projetoNome,
            OffsetDateTime inicioPlanejado,
            OffsetDateTime fimPlanejado,
            String status,
            String responsavel,
            String cor,
            OffsetDateTime criadoEm) {}

    public record PostponeGlobalRiskRequest(
            OffsetDateTime novaDataFim,
            String motivo) {}

    public record GlobalRiskHistoryEvent(
            String id,
            String tipo,
            String descricao,
            String statusAnterior,
            String statusNovo,
            OffsetDateTime dataFimAnterior,
            OffsetDateTime dataFimNova,
            String motivo,
            OffsetDateTime criadoEm) {}

    public record ProjectOverviewResponse(
            String id,
            String nome,
            String descricao,
            String status,
            int percentualConclusao,
            int totalEtapas,
            int etapasConcluidas,
            int totalAlocacoes,
            BigDecimal custoPlanejadoEquipe,
            BigDecimal totalReceitas,
            BigDecimal totalDespesas,
            BigDecimal saldo,
            String situacao,
            String donoProjeto) {}

    
    public record VagaHistorico(
            String alias,   // e.g. "VAG0031164"
            String url,     // URL da vaga (pode ser null)
            String inicio,  // "yyyy-MM-dd" ou null
            String fim)     // "yyyy-MM-dd" ou null
    {}

    public record Person(
            String id,
            String nome,
            String perfilId,
            String perfilNome,
            String tipoVinculo,
            String consultoria,
            BigDecimal valorHora,
            BigDecimal valorMensal,
            String vagaUrl,
            String vagaAlias,
            String dataNascimento,   
            String contato,          
            Boolean ativo,           
            java.util.List<VagaHistorico> vagasAnteriores, 
            OffsetDateTime criadoEm) {}

    public record CreatePersonRequest(
            String nome,
            String perfilId,
            String tipoVinculo,
            String consultoria,
            BigDecimal valorHora,
            BigDecimal valorMensal,
            String vagaUrl,
            String vagaAlias,
            String dataNascimento,
            String contato,
            boolean ativo,
            java.util.List<VagaHistorico> vagasAnteriores) {}

    
    public record Absence(
            String id,
            String pessoaId,
            String pessoaNome,
            String tipo,           
            String inicio,         
            String fim,            
            boolean recorrente,    
            String observacao,
            OffsetDateTime criadoEm) {}

    public record CreateAbsenceRequest(
            String pessoaId,
            String pessoaNome,
            String tipo,
            String inicio,
            String fim,
            boolean recorrente,
            String observacao) {}

    public record ImportPeopleCsvRequest(String csv) {}
    public record ImportPeopleCsvResponse(int criados, int ignorados) {}
    public record ImportConsultanciesCsvRequest(String csv) {}

    public record Consultancy(
            String id,
            String nome,
            String descricao,
            String telefone,
            String email,
            String contato,
            OffsetDateTime criadoEm) {}

    public record CreateConsultancyRequest(
            String nome,
            String descricao,
            String telefone,
            String email,
            String contato) {}
    public record ImportFocalPointsCsvRequest(String csv) {}

    public record ImportRisksCsvRequest(String csv) {}
    public record ImportIncidentsCsvRequest(String csv) {}

    public record ImportCsvResponse(int criados, int ignorados) {}

    public record AllocationPaymentState(
            String allocationId,
            int month,
            boolean paid,
            OffsetDateTime updatedAt,
            String updatedBy) {}

    public record UpdateAllocationPaymentRequest(Boolean paid) {}

    public record LoPresenceState(
            String loId,
            String username,
            OffsetDateTime updatedAt) {}

    public record UpsertLoPresenceRequest(String loId) {}

    public record AllocationMonthlyState(
            String allocationId,
            int month,
            Boolean canceled,
            BigDecimal manualValue,
            BigDecimal manualPercent,
            OffsetDateTime updatedAt,
            String updatedBy) {}

    public record UpdateAllocationMonthlyStateRequest(
            Boolean canceled,
            BigDecimal manualValue,
            BigDecimal manualPercent) {}

    public record AllocationCursorState(
            String username,
            String loId,
            BigDecimal x,
            BigDecimal y,
            OffsetDateTime updatedAt) {}

    public record UpsertAllocationCursorRequest(
            String loId,
            BigDecimal x,
            BigDecimal y) {}

    public record MonthlyHours(
            int mes,
            BigDecimal horas,
            OffsetDateTime atualizadoEm) {}

    public record UpsertMonthlyHoursRequest(BigDecimal horas) {}

    public record FocalPoint(
            String id,
            String area,
            String responsavelPor,
            String email,
            String telefone,
            OffsetDateTime criadoEm) {}

    public record CreateFocalPointRequest(
            String area,
            String responsavelPor,
            String email,
            String telefone) {}

    // ── Incidents ─────────────────────────────────────────────────────────────
    // tipo: DISPONIBILIDADE | PERFORMANCE | SEGURANCA | DADOS | INTEGRACAO | OUTROS
    // severidade: P1_CRITICO | P2_ALTO | P3_MEDIO | P4_BAIXO
    // status: ABERTO | EM_INVESTIGACAO | RESOLVIDO | POS_MORTEM
    public record IncidentHistoryEvent(
            String id,
            String tipo,          
            String descricao,
            String statusAnterior,
            String statusNovo,
            OffsetDateTime criadoEm) {}

    public record Incident(
            String id,
            String titulo,
            String descricao,
            String tipo,
            String severidade,
            String status,
            String responsavel,
            OffsetDateTime dataOcorrencia,
            OffsetDateTime dataResolucao,
            String impacto,
            String causaRaiz,
            String acoesCorrativas,
            List<IncidentHistoryEvent> historico,
            OffsetDateTime criadoEm,
            String criadoPor) {}  

    public record CreateIncidentRequest(
            String titulo,
            String descricao,
            String tipo,
            String severidade,
            String status,
            String responsavel,
            OffsetDateTime dataOcorrencia,
            OffsetDateTime dataResolucao,
            String impacto,
            String causaRaiz,
            String acoesCorrativas) {}

    
    
    
    
    
    public record TechnicalDebtHistoryEvent(
            String id,
            String tipo,          
            String descricao,
            String statusAnterior,
            String statusNovo,
            OffsetDateTime criadoEm) {}

    public record TechnicalDebt(
            String id,
            String titulo,
            String descricao,
            String categoria,
            String impacto,
            int esforcoEstimado,
            String prioridade,
            String status,
            String responsavel,
            String projetoRef,
            OffsetDateTime dataAlvo,
            OffsetDateTime resolvidoEm,
            List<TechnicalDebtHistoryEvent> historico,
            OffsetDateTime criadoEm,
            String criadoPor) {}  // username do criador — null em registros legados

    public record CreateTechnicalDebtRequest(
            String titulo,
            String descricao,
            String categoria,
            String impacto,
            Integer esforcoEstimado,
            String prioridade,
            String status,
            String responsavel,
            String projetoRef,
            OffsetDateTime dataAlvo) {}

    
    
    
    
    
    
    public record IndicatorAction(
            String id,
            String descricao,
            String responsavel,
            String status,          
            int cicloAberto,
            Integer cicloConcluido,
            OffsetDateTime prazo,
            OffsetDateTime concluidoEm,
            OffsetDateTime criadoEm) {}

    public record IndicatorCycle(
            String id,
            int numero,
            Double valor,
            String observacao,
            OffsetDateTime dataReferencia,
            OffsetDateTime criadoEm) {}

    public record Indicator(
            String id,
            String titulo,
            String descricao,
            String tipo,
            String categoria,
            String unidade,
            Double meta,
            String polaridade,
            String frequencia,
            String responsavel,
            String status,
            List<IndicatorCycle> ciclos,
            List<IndicatorAction> acoes,
            OffsetDateTime criadoEm,
            String criadoPor) {}  // username do criador — null em registros legados

    public record CreateIndicatorRequest(
            String titulo,
            String descricao,
            String tipo,
            String categoria,
            String unidade,
            Double meta,
            String polaridade,
            String frequencia,
            String responsavel,
            String status) {}

    public record CreateIndicatorCycleRequest(
            Double valor,
            String observacao,
            OffsetDateTime dataReferencia,
            List<String> acoesConcluidasIds) {}

    public record CreateIndicatorActionRequest(
            String descricao,
            String responsavel,
            OffsetDateTime prazo) {}

    public record UpdateIndicatorActionRequest(
            String descricao,
            String responsavel,
            String status,
            OffsetDateTime prazo) {}

    public record UpdateIndicatorCycleRequest(
            Double valor,
            String observacao,
            OffsetDateTime dataReferencia) {}

    
    // ── Allocation Percent (general allocation % per allocation, saved to backend) ─────────
    public record AllocationPercentConfig(
            String allocationId,
            java.math.BigDecimal percentual,
            OffsetDateTime updatedAt,
            String updatedBy) {}

    public record UpsertAllocationPercentRequest(java.math.BigDecimal percentual) {}

    // ── LO Realizado (realized monthly value per LO, saved to backend) ───────────────────
    public record LoRealizadoConfig(
            String loId,
            int month,
            java.math.BigDecimal valor,
            OffsetDateTime updatedAt,
            String updatedBy) {}

    public record UpsertLoRealizadoRequest(java.math.BigDecimal valor) {}

    public record GanttMarker(String id, String label, String date, String description) {}
    public record GanttItemMeta(String responsavel, int pct) {}
    public record GanttProjectConfig(
            String projectId,
            List<GanttMarker> markers,
            java.util.Map<String, GanttItemMeta> meta,
            Integer rowHeight,
            java.util.Map<String, Integer> rowHeights) {}
    public record SaveGanttConfigRequest(
            List<GanttMarker> markers,
            java.util.Map<String, GanttItemMeta> meta,
            Integer rowHeight,
            java.util.Map<String, Integer> rowHeights) {}

    public record TransferOwnershipRequest(String novoDono) {}

    public record ReplaceOwnershipRequest(
            String oldValue,
            String newValue) {}

    public record ReplaceOwnershipResponse(
            int projetosDonoAtualizados,
            int cronogramasResponsavelAtualizados,
            int linhasOrcamentariasDonoAtualizadas,
            int riscosResponsavelAtualizados,
            int incidentesResponsavelAtualizados,
            int debitosResponsavelAtualizados,
            int indicadoresResponsavelAtualizados,
            int acoesIndicadorResponsavelAtualizadas,
            int totalAtualizacoes) {}

    
    public record FeriadoEntry(String id, String data, String nome, String tipo) {}
    public record FederalOverrideEntry(String nomeCustom, boolean disabled) {}
    public record FeriadosConfig(
            List<FeriadoEntry> feriados,
            java.util.Map<String, FederalOverrideEntry> federalOverrides,
            List<Boolean> diasUteis) {}
}
