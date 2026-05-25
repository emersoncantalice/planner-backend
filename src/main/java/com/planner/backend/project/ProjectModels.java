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
            String tipoAlteracao,        // "MOVER" | "REDIMENSIONAR_INICIO" | "REDIMENSIONAR_FIM" | "REDIMENSIONAR"
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
            String situacao,      // "DRAFT" | "PUBLISHED" — null treated as "PUBLISHED" (legacy)
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
            String situacao,  // "DRAFT" | "PUBLISHED"
            String dono) {}   // username of creator

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
            OffsetDateTime criadoEm) {}

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
            String dataNascimento,   // "yyyy-MM-dd" or null
            String contato,          // phone / e-mail or null
            Boolean ativo,           // null in legacy records = treat as true
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
            boolean ativo) {}

    // ── Ausências / férias ───────────────────────────────────────────────────
    public record Absence(
            String id,
            String pessoaId,
            String pessoaNome,
            String tipo,           // FERIAS | AUSENCIA | LICENCA | OUTRO
            String inicio,         // "yyyy-MM-dd"
            String fim,            // "yyyy-MM-dd"
            boolean recorrente,    // se true repete todo ano (usa só mm-dd)
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
            String tipo,          // CRIACAO | STATUS | EDICAO
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
            OffsetDateTime criadoEm) {}

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

    // ── Technical Debt ────────────────────────────────────────────────────────
    // categoria: ARQUITETURA | CODIGO | TESTES | DOCUMENTACAO | INFRAESTRUTURA | SEGURANCA | OUTROS
    // impacto: BAIXO | MEDIO | ALTO | CRITICO
    // prioridade: BAIXA | MEDIA | ALTA | URGENTE
    // status: IDENTIFICADO | EM_TRATAMENTO | RESOLVIDO | ACEITO
    public record TechnicalDebtHistoryEvent(
            String id,
            String tipo,          // CRIACAO | STATUS | EDICAO
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
            OffsetDateTime criadoEm) {}

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

    // ── Indicators ────────────────────────────────────────────────────────────
    // tipo: TECNICO | NEGOCIO
    // categoria: QUALIDADE | PRODUTIVIDADE | FINANCEIRO | ATENDIMENTO | SEGURANCA | OUTROS
    // polaridade: MAIOR_MELHOR | MENOR_MELHOR
    // frequencia: SEMANAL | QUINZENAL | MENSAL | TRIMESTRAL
    // status: ATIVO | INATIVO
    public record IndicatorAction(
            String id,
            String descricao,
            String responsavel,
            String status,          // ABERTA | CONCLUIDA
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
            OffsetDateTime criadoEm) {}

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
}
