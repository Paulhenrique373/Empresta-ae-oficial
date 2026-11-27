package com.example.emprestaa.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/* ------------------------------------------------------------------ */
/* Categorias e períodos                                                */
/* ------------------------------------------------------------------ */

enum class CategoriaRelatorio(val rotulo: String, val arquivo: String) {
    GERAL("Geral", "geral"),
    EMPRESTIMOS("Empréstimos", "emprestimos"),
    EQUIPAMENTOS("Equipamentos", "equipamentos"),
    OCORRENCIAS("Ocorrências", "ocorrencias")
}

enum class TipoPeriodo(val rotulo: String) {
    TUDO("Todo o período"),
    HOJE("Hoje"),
    ULTIMOS_7("Últimos 7 dias"),
    ULTIMOS_30("Últimos 30 dias"),
    ESTE_MES("Este mês"),
    ESTE_ANO("Este ano"),
    PERSONALIZADO("Personalizado")
}

/**
 * Intervalo já resolvido.
 * inicioMs é inclusivo e fimMs é exclusivo; null = sem limite.
 */
data class IntervaloRelatorio(
    val tipo: TipoPeriodo,
    val inicio: LocalDate?,
    val fim: LocalDate?,
    val inicioMs: Long?,
    val fimMs: Long?,
    val descricao: String
) {
    /** true quando o período limita as datas (qualquer opção diferente de "Todo o período"). */
    val limitado: Boolean get() = inicioMs != null || fimMs != null
}

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val FORMATO_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy")
private val FORMATO_GERADO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val LOCAL_BR: Locale = Locale.forLanguageTag("pt-BR")

internal fun calcularIntervalo(
    tipo: TipoPeriodo,
    hoje: LocalDate,
    zona: ZoneId,
    inicioPersonalizado: LocalDate?,
    fimPersonalizado: LocalDate?
): IntervaloRelatorio {

    val par: Pair<LocalDate?, LocalDate?> = when (tipo) {
        TipoPeriodo.TUDO -> Pair(null, null)
        TipoPeriodo.HOJE -> Pair(hoje, hoje)
        TipoPeriodo.ULTIMOS_7 -> Pair(hoje.minusDays(6), hoje)
        TipoPeriodo.ULTIMOS_30 -> Pair(hoje.minusDays(29), hoje)
        TipoPeriodo.ESTE_MES -> Pair(hoje.withDayOfMonth(1), hoje)
        TipoPeriodo.ESTE_ANO -> Pair(hoje.withDayOfYear(1), hoje)
        TipoPeriodo.PERSONALIZADO ->
            if (inicioPersonalizado != null && fimPersonalizado != null) {
                if (inicioPersonalizado <= fimPersonalizado) {
                    Pair(inicioPersonalizado, fimPersonalizado)
                } else {
                    Pair(fimPersonalizado, inicioPersonalizado)
                }
            } else {
                Pair(null, null)
            }
    }

    val inicio = par.first
    val fim = par.second

    val descricao = when {
        inicio == null || fim == null ->
            if (tipo == TipoPeriodo.PERSONALIZADO) {
                "Personalizado (datas ainda não escolhidas; mostrando todo o período)"
            } else {
                "Todo o período"
            }

        inicio == fim -> "${tipo.rotulo} (${inicio.format(FORMATO_DATA)})"

        else ->
            "${tipo.rotulo} (${inicio.format(FORMATO_DATA)} a ${fim.format(FORMATO_DATA)})"
    }

    return IntervaloRelatorio(
        tipo = tipo,
        inicio = inicio,
        fim = fim,
        inicioMs = inicio?.atStartOfDay(zona)?.toInstant()?.toEpochMilli(),
        fimMs = fim?.plusDays(1)?.atStartOfDay(zona)?.toInstant()?.toEpochMilli(),
        descricao = descricao
    )
}

/** Data dentro do intervalo? Sem limite, tudo entra (inclusive registros sem data). */
internal fun dentroDoIntervalo(ms: Long?, intervalo: IntervaloRelatorio): Boolean {
    if (!intervalo.limitado) return true
    if (ms == null) return false

    val inicio = intervalo.inicioMs
    val fim = intervalo.fimMs

    return (inicio == null || ms >= inicio) && (fim == null || ms < fim)
}

/* ------------------------------------------------------------------ */
/* Modelos dos relatórios                                               */
/* ------------------------------------------------------------------ */

data class ItemContagem(val rotulo: String, val valor: Int)

data class PontoTempo(val rotulo: String, val valor: Int)

/** Dados lidos do Supabase, já combinados. O período é aplicado depois, sem nova consulta. */
data class DadosRelatorios(
    val equipamentos: List<EquipamentoRow>,
    val emprestimos: List<EmprestimoGestao>,
    val ocorrencias: List<OcorrenciaGestao>
)

/** Situação ATUAL dos equipamentos. Não depende do período. */
data class RelatorioEquipamentos(
    val total: Int,
    val disponiveis: Int,
    val emUso: Int,
    val manutencao: Int,
    val outras: Int,
    val porSituacao: List<ItemContagem>,
    val porTipo: List<ItemContagem>,
    val porMarca: List<ItemContagem>,
    val lista: List<EquipamentoRow>
)

data class RelatorioEmprestimos(
    /** Empréstimos cuja retirada está no período. */
    val retiradas: Int,
    /** Entre as retiradas do período, quantas estão ativas. */
    val ativos: Int,
    /** Entre as retiradas do período, quantas foram devolvidas. */
    val devolvidos: Int,
    /** Devoluções cuja data de devolução está no período. */
    val devolucoesRealizadas: Int,
    /** Total de empréstimos ativos neste momento (não depende do período). */
    val ativosAgora: Int,
    /** Registros sem data que ficaram fora de um período específico. */
    val semData: Int,
    val serie: List<PontoTempo>,
    val serieAgrupamento: String,
    val maisUtilizados: List<ItemContagem>,
    val professores: List<ItemContagem>,
    val professoresTotal: Int,
    val historico: List<EmprestimoGestao>
)

data class RelatorioOcorrencias(
    val total: Int,
    val pendentes: Int,
    val emAnalise: Int,
    val resolvidas: Int,
    val semData: Int,
    val porSituacao: List<ItemContagem>,
    val porTipo: List<ItemContagem>,
    val equipamentosMaisOcorrencias: List<ItemContagem>,
    val serie: List<PontoTempo>,
    val serieAgrupamento: String,
    val registros: List<OcorrenciaGestao>
)

data class RelatoriosPeriodo(
    val intervalo: IntervaloRelatorio,
    val geradoEm: String,
    val equipamentos: RelatorioEquipamentos,
    val emprestimos: RelatorioEmprestimos,
    val ocorrencias: RelatorioOcorrencias,
    val professoresIndisponiveis: Boolean
)

/* ------------------------------------------------------------------ */
/* Documento para exportação (PDF e CSV usam o mesmo modelo)            */
/* ------------------------------------------------------------------ */

data class SecaoExportacao(
    val titulo: String,
    val cabecalho: List<String>,
    val linhas: List<List<String>>,
    val nota: String? = null
)

data class DocumentoExportacao(
    val titulo: String,
    val geradoEm: String,
    val periodo: String,
    val secoes: List<SecaoExportacao>
)

/* ------------------------------------------------------------------ */
/* Cálculos (funções puras, sem rede)                                   */
/* ------------------------------------------------------------------ */

internal fun percentualTexto(valor: Int, total: Int): String =
    if (total <= 0) {
        "0,0%"
    } else {
        String.format(LOCAL_BR, "%.1f%%", valor * 100.0 / total)
    }

private fun contar(valores: List<String?>, semValor: String): List<ItemContagem> =
    valores
        .map { it?.trim().orEmpty() }
        .groupBy { normalizarTexto(it) }
        .map { (_, itens) ->
            ItemContagem(
                itens.first().ifBlank { semValor }.replaceFirstChar { c -> c.uppercase() },
                itens.size
            )
        }
        .sortedWith(
            compareByDescending<ItemContagem> { it.valor }.thenBy { it.rotulo }
        )

internal fun limitarComOutras(itens: List<ItemContagem>, limite: Int): List<ItemContagem> =
    if (itens.size <= limite) {
        itens
    } else {
        itens.take(limite) +
                ItemContagem("Outras", itens.drop(limite).sumOf { it.valor })
    }

private fun rotuloEquipamento(nome: String?, etiqueta: String?, id: Long?): String {
    val base = nome ?: if (id != null) "Equipamento nº $id" else "Equipamento não identificado"
    return if (etiqueta != null) "$base · $etiqueta" else base
}

/**
 * Agrupa datas por dia (períodos de até 31 dias) ou por mês.
 * Só devolve pontos quando existe ao menos uma data.
 */
internal fun montarSerie(
    datasMs: List<Long>,
    intervalo: IntervaloRelatorio,
    zona: ZoneId
): Pair<List<PontoTempo>, String> {

    if (datasMs.isEmpty()) return Pair(emptyList(), "dia")

    val dias = datasMs.map {
        Instant.ofEpochMilli(it).atZone(zona).toLocalDate()
    }

    val inicio = intervalo.inicio ?: dias.min()
    val fim = intervalo.fim ?: dias.max()

    val span = ChronoUnit.DAYS.between(inicio, fim) + 1

    return if (span <= 31) {

        val porDia = dias.groupingBy { it }.eachCount()
        val pontos = mutableListOf<PontoTempo>()
        var dia = inicio

        while (!dia.isAfter(fim)) {
            pontos.add(PontoTempo(dia.format(FORMATO_DIA), porDia[dia] ?: 0))
            dia = dia.plusDays(1)
        }

        Pair(pontos, "dia")

    } else {

        val porMes = dias.groupingBy { YearMonth.from(it) }.eachCount()
        val pontos = mutableListOf<PontoTempo>()
        var mes = YearMonth.from(inicio)
        val ultimo = YearMonth.from(fim)

        while (!mes.isAfter(ultimo)) {
            pontos.add(PontoTempo(mes.format(FORMATO_MES), porMes[mes] ?: 0))
            mes = mes.plusMonths(1)
        }

        Pair(pontos, "mês")
    }
}

internal fun calcularRelatorioEquipamentos(
    equipamentos: List<EquipamentoRow>
): RelatorioEquipamentos {

    fun contarSituacao(situacao: String) =
        equipamentos.count { normalizarTexto(it.status) == situacao }

    val disponiveis = contarSituacao("disponivel")
    val emUso = contarSituacao("em uso")
    val manutencao = contarSituacao("manutencao")
    val outras = equipamentos.size - disponiveis - emUso - manutencao

    val porSituacao = buildList {
        add(ItemContagem("Disponíveis", disponiveis))
        add(ItemContagem("Em uso", emUso))
        add(ItemContagem("Em manutenção", manutencao))
        if (outras > 0) add(ItemContagem("Outras situações", outras))
    }

    return RelatorioEquipamentos(
        total = equipamentos.size,
        disponiveis = disponiveis,
        emUso = emUso,
        manutencao = manutencao,
        outras = outras,
        porSituacao = porSituacao,
        porTipo = contar(equipamentos.map { it.tipo }, "Sem tipo"),
        porMarca = limitarComOutras(
            contar(equipamentos.map { it.marca }, "Sem marca"),
            6
        ),
        lista = equipamentos.sortedBy { normalizarTexto(it.etiqueta) }
    )
}

internal fun calcularRelatorioEmprestimos(
    todos: List<EmprestimoGestao>,
    intervalo: IntervaloRelatorio,
    zona: ZoneId
): RelatorioEmprestimos {

    val noPeriodo = todos.filter { dentroDoIntervalo(it.retiradaMs, intervalo) }

    val (serie, agrupamento) = montarSerie(
        noPeriodo.mapNotNull { it.retiradaMs },
        intervalo,
        zona
    )

    val maisUtilizados = noPeriodo
        .groupBy { it.equipamentoId }
        .map { (id, itens) ->
            val primeiro = itens.first()
            ItemContagem(
                rotuloEquipamento(primeiro.equipamento, primeiro.etiqueta, id),
                itens.size
            )
        }
        .sortedWith(compareByDescending<ItemContagem> { it.valor }.thenBy { it.rotulo })
        .take(5)

    val porProfessor = noPeriodo.filter { it.usuarioId != null }.groupBy { it.usuarioId }

    val professores = porProfessor
        .map { (_, itens) ->
            ItemContagem(
                itens.firstNotNullOfOrNull { it.professor } ?: "Professor não identificado",
                itens.size
            )
        }
        .sortedWith(compareByDescending<ItemContagem> { it.valor }.thenBy { it.rotulo })

    return RelatorioEmprestimos(
        retiradas = noPeriodo.size,
        ativos = noPeriodo.count { it.ativo },
        devolvidos = noPeriodo.count { it.devolvido },
        devolucoesRealizadas = todos.count {
            it.devolvido && dentroDoIntervalo(it.devolucaoMs, intervalo)
        },
        ativosAgora = todos.count { it.ativo },
        semData = if (intervalo.limitado) todos.count { it.retiradaMs == null } else 0,
        serie = serie,
        serieAgrupamento = agrupamento,
        maisUtilizados = maisUtilizados,
        professores = professores.take(10),
        professoresTotal = porProfessor.size,
        historico = noPeriodo
    )
}

internal fun calcularRelatorioOcorrencias(
    todas: List<OcorrenciaGestao>,
    intervalo: IntervaloRelatorio,
    zona: ZoneId
): RelatorioOcorrencias {

    val noPeriodo = todas.filter { dentroDoIntervalo(it.dataMs, intervalo) }

    val (serie, agrupamento) = montarSerie(
        noPeriodo.mapNotNull { it.dataMs },
        intervalo,
        zona
    )

    val equipamentosMais = noPeriodo
        .filter { it.equipamentoId != null }
        .groupBy { it.equipamentoId }
        .map { (id, itens) ->
            val primeiro = itens.first()
            ItemContagem(
                rotuloEquipamento(primeiro.equipamento, primeiro.etiqueta, id),
                itens.size
            )
        }
        .sortedWith(compareByDescending<ItemContagem> { it.valor }.thenBy { it.rotulo })
        .take(5)

    return RelatorioOcorrencias(
        total = noPeriodo.size,
        pendentes = noPeriodo.count { it.categoria == CategoriaOcorrencia.PENDENTE },
        emAnalise = noPeriodo.count { it.categoria == CategoriaOcorrencia.EM_ANALISE },
        resolvidas = noPeriodo.count { it.categoria == CategoriaOcorrencia.RESOLVIDA },
        semData = if (intervalo.limitado) todas.count { it.dataMs == null } else 0,
        porSituacao = contar(noPeriodo.map { it.situacao }, "Sem situação"),
        porTipo = limitarComOutras(
            contar(noPeriodo.map { it.tipoDefeito }, "Tipo não informado"),
            6
        ),
        equipamentosMaisOcorrencias = equipamentosMais,
        serie = serie,
        serieAgrupamento = agrupamento,
        registros = noPeriodo
    )
}

internal fun calcularRelatorios(
    dados: DadosRelatorios,
    intervalo: IntervaloRelatorio,
    zona: ZoneId,
    professoresIndisponiveis: Boolean
): RelatoriosPeriodo =
    RelatoriosPeriodo(
        intervalo = intervalo,
        geradoEm = java.time.LocalDateTime.now().format(FORMATO_GERADO),
        equipamentos = calcularRelatorioEquipamentos(dados.equipamentos),
        emprestimos = calcularRelatorioEmprestimos(dados.emprestimos, intervalo, zona),
        ocorrencias = calcularRelatorioOcorrencias(dados.ocorrencias, intervalo, zona),
        professoresIndisponiveis = professoresIndisponiveis
    )

/** Existe algo para exportar nesta categoria? Evita arquivos vazios. */
internal fun relatorioTemDados(categoria: CategoriaRelatorio, r: RelatoriosPeriodo): Boolean =
    when (categoria) {
        CategoriaRelatorio.GERAL ->
            r.equipamentos.total > 0 || r.emprestimos.retiradas > 0 || r.ocorrencias.total > 0
        CategoriaRelatorio.EMPRESTIMOS ->
            r.emprestimos.retiradas > 0 || r.emprestimos.devolucoesRealizadas > 0
        CategoriaRelatorio.EQUIPAMENTOS -> r.equipamentos.total > 0
        CategoriaRelatorio.OCORRENCIAS -> r.ocorrencias.total > 0
    }

/* ------------------------------------------------------------------ */
/* Montagem do documento exportado                                      */
/* ------------------------------------------------------------------ */

private fun indicadores(
    titulo: String,
    pares: List<Pair<String, Int>>,
    nota: String? = null
) = SecaoExportacao(
    titulo = titulo,
    cabecalho = listOf("Indicador", "Valor"),
    linhas = pares.map { listOf(it.first, it.second.toString()) },
    nota = nota
)

private fun distribuicao(
    titulo: String,
    coluna: String,
    itens: List<ItemContagem>
): SecaoExportacao? {
    if (itens.isEmpty()) return null
    val total = itens.sumOf { it.valor }
    return SecaoExportacao(
        titulo = titulo,
        cabecalho = listOf(coluna, "Quantidade", "Percentual"),
        linhas = itens.map {
            listOf(it.rotulo, it.valor.toString(), percentualTexto(it.valor, total))
        }
    )
}

private fun ranking(
    titulo: String,
    coluna: String,
    itens: List<ItemContagem>
): SecaoExportacao? =
    if (itens.isEmpty()) null
    else SecaoExportacao(
        titulo = titulo,
        cabecalho = listOf(coluna, "Quantidade"),
        linhas = itens.map { listOf(it.rotulo, it.valor.toString()) }
    )

private fun serieSecao(
    titulo: String,
    agrupamento: String,
    serie: List<PontoTempo>
): SecaoExportacao? =
    if (serie.isEmpty()) null
    else SecaoExportacao(
        titulo = "$titulo (por $agrupamento)",
        cabecalho = listOf(agrupamento.replaceFirstChar { it.uppercase() }, "Quantidade"),
        linhas = serie.map { listOf(it.rotulo, it.valor.toString()) }
    )

private fun notaSemData(qtd: Int): String? =
    if (qtd > 0) "$qtd registro(s) sem data ficaram fora deste período." else null

private const val NOTA_ATUAL = "Situação atual dos equipamentos. Não depende do período escolhido."

internal fun montarDocumento(
    categoria: CategoriaRelatorio,
    r: RelatoriosPeriodo
): DocumentoExportacao {

    val eq = r.equipamentos
    val em = r.emprestimos
    val oc = r.ocorrencias

    val secoes = mutableListOf<SecaoExportacao?>()

    when (categoria) {

        CategoriaRelatorio.GERAL -> {
            secoes += indicadores(
                "Equipamentos (situação atual)",
                buildList {
                    add("Total de equipamentos" to eq.total)
                    add("Equipamentos disponíveis" to eq.disponiveis)
                    add("Equipamentos em uso" to eq.emUso)
                    add("Equipamentos em manutenção" to eq.manutencao)
                    if (eq.outras > 0) add("Outras situações" to eq.outras)
                },
                NOTA_ATUAL
            )
            secoes += indicadores(
                "Empréstimos (retirados no período)",
                listOf(
                    "Total de empréstimos" to em.retiradas,
                    "Empréstimos ativos" to em.ativos,
                    "Empréstimos devolvidos" to em.devolvidos
                ),
                notaSemData(em.semData)
            )
            secoes += indicadores(
                "Ocorrências (registradas no período)",
                listOf(
                    "Total de ocorrências" to oc.total,
                    "Ocorrências pendentes (Registrado)" to oc.pendentes,
                    "Ocorrências em análise (Em manutenção)" to oc.emAnalise,
                    "Ocorrências resolvidas (Consertado)" to oc.resolvidas
                ),
                notaSemData(oc.semData)
            )
        }

        CategoriaRelatorio.EMPRESTIMOS -> {
            secoes += indicadores(
                "Indicadores (retiradas no período)",
                listOf(
                    "Empréstimos registrados no período" to em.retiradas,
                    "Devoluções realizadas no período" to em.devolucoesRealizadas,
                    "Ativos entre os registrados no período" to em.ativos,
                    "Devolvidos entre os registrados no período" to em.devolvidos,
                    "Empréstimos ativos agora (atual)" to em.ativosAgora,
                    "Professores com empréstimo no período" to em.professoresTotal
                ),
                notaSemData(em.semData)
            )
            secoes += serieSecao("Empréstimos", em.serieAgrupamento, em.serie)
            secoes += ranking("Equipamentos mais utilizados", "Equipamento", em.maisUtilizados)
            secoes += ranking("Professores com mais empréstimos", "Professor", em.professores)
            if (em.historico.isNotEmpty()) {
                secoes += SecaoExportacao(
                    titulo = "Histórico de empréstimos",
                    cabecalho = listOf(
                        "Nº", "Professor", "Equipamento", "Etiqueta",
                        "Tipo", "Retirada", "Devolução", "Situação"
                    ),
                    linhas = em.historico.map {
                        listOf(
                            it.id.toString(),
                            it.professor ?: "Não identificado",
                            it.equipamento ?: "Não identificado",
                            it.etiqueta ?: "",
                            it.tipo ?: "",
                            it.retiradaTexto ?: "",
                            it.devolucaoTexto ?: "",
                            it.situacao
                        )
                    }
                )
            }
        }

        CategoriaRelatorio.EQUIPAMENTOS -> {
            secoes += indicadores(
                "Indicadores (situação atual)",
                buildList {
                    add("Total de equipamentos" to eq.total)
                    add("Disponíveis" to eq.disponiveis)
                    add("Emprestados (em uso)" to eq.emUso)
                    add("Em manutenção" to eq.manutencao)
                    if (eq.outras > 0) add("Outras situações" to eq.outras)
                },
                NOTA_ATUAL
            )
            secoes += distribuicao("Equipamentos por situação", "Situação", eq.porSituacao)
            secoes += distribuicao("Equipamentos por tipo", "Tipo", eq.porTipo)
            secoes += distribuicao("Equipamentos por marca", "Marca", eq.porMarca)
            if (eq.lista.isNotEmpty()) {
                secoes += SecaoExportacao(
                    titulo = "Lista de equipamentos",
                    cabecalho = listOf("Etiqueta", "Marca", "Modelo", "Tipo", "Situação"),
                    linhas = eq.lista.map {
                        listOf(it.etiqueta, it.marca, it.modelo, it.tipo, it.status)
                    }
                )
            }
        }

        CategoriaRelatorio.OCORRENCIAS -> {
            secoes += indicadores(
                "Indicadores (registradas no período)",
                listOf(
                    "Total de ocorrências" to oc.total,
                    "Pendentes (Registrado)" to oc.pendentes,
                    "Em análise (Em manutenção)" to oc.emAnalise,
                    "Resolvidas (Consertado)" to oc.resolvidas
                ),
                notaSemData(oc.semData)
            )
            secoes += distribuicao("Ocorrências por situação", "Situação", oc.porSituacao)
            secoes += distribuicao("Ocorrências por tipo de defeito", "Tipo de defeito", oc.porTipo)
            secoes += ranking(
                "Equipamentos com mais ocorrências",
                "Equipamento",
                oc.equipamentosMaisOcorrencias
            )
            secoes += serieSecao("Ocorrências", oc.serieAgrupamento, oc.serie)
            if (oc.registros.isNotEmpty()) {
                secoes += SecaoExportacao(
                    titulo = "Registros de ocorrências",
                    cabecalho = listOf(
                        "Nº", "Equipamento", "Etiqueta", "Professor",
                        "Tipo de defeito", "Descrição", "Data", "Situação"
                    ),
                    linhas = oc.registros.map {
                        listOf(
                            it.id.toString(),
                            it.equipamento ?: "Não identificado",
                            it.etiqueta ?: "",
                            it.professor ?: "Não identificado",
                            it.tipoDefeito ?: "",
                            it.descricao ?: "",
                            it.dataTexto ?: "",
                            it.situacao
                        )
                    }
                )
            }
        }
    }

    val periodo = when (categoria) {
        CategoriaRelatorio.EQUIPAMENTOS ->
            "Situação atual (o período não se aplica a este relatório)"
        else -> r.intervalo.descricao
    }

    return DocumentoExportacao(
        titulo = "Relatório de ${categoria.rotulo.lowercase()}",
        geradoEm = r.geradoEm,
        periodo = periodo,
        secoes = secoes.filterNotNull()
    )
}

/* ------------------------------------------------------------------ */
/* ViewModel                                                            */
/* ------------------------------------------------------------------ */

data class GestaoRelatoriosUiState(
    val carregando: Boolean = true,
    val atualizando: Boolean = false,
    val acessoNegado: Boolean = false,
    val erro: String? = null,
    val dados: DadosRelatorios? = null,
    val professoresIndisponiveis: Boolean = false,
    val atualizadoEm: String? = null
)

private const val VALIDADE_LEITURA_MS = 60_000L

private fun mensagemRelatorios(erro: Throwable): String {
    val texto = (erro.message ?: "").lowercase()

    return if ("usuario_e_gestao" in texto) {
        "Não foi possível confirmar o acesso da Gestão no servidor. Avise o suporte técnico."
    } else {
        traduzirErro(erro)
    }
}

/**
 * Relatórios administrativos da Gestão. Somente consulta.
 *
 * Reaproveita EquipamentoRepository, EmprestimoRepository,
 * DefeitoRepository e UsuarioRepository. O período é aplicado na
 * tela sobre os dados já lidos, sem nova consulta.
 * O perfil é validado no servidor (usuario_e_gestao) e a RLS
 * continua decidindo o que a conta consegue ler.
 */
class GestaoRelatoriosViewModel(
    private val equipamentos: EquipamentoRepository = EquipamentoRepository(),
    private val emprestimos: EmprestimoRepository = EmprestimoRepository(),
    private val defeitos: DefeitoRepository = DefeitoRepository(),
    private val usuarios: UsuarioRepository = UsuarioRepository(),
    private val gestao: GestaoRepository = GestaoRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(GestaoRelatoriosUiState())
    val state = _state.asStateFlow()

    private var trabalho: Job? = null
    private var ultimaLeituraMs = 0L

    /**
     * forcar = true é o botão de atualizar.
     * Sem forçar, uma leitura feita há menos de 60 segundos é reaproveitada
     * (evita consultas ao voltar para a tela).
     */
    fun carregar(forcar: Boolean = false) {

        if (trabalho?.isActive == true) return

        val atual = _state.value

        if (
            !forcar &&
            atual.dados != null &&
            System.currentTimeMillis() - ultimaLeituraMs < VALIDADE_LEITURA_MS
        ) return

        trabalho = viewModelScope.launch {

            _state.value = atual.copy(
                carregando = atual.dados == null,
                atualizando = atual.dados != null,
                erro = null,
                acessoNegado = false
            )

            runCatching {
                if (gestao.usuarioEGestao()) lerTudo() else null
            }.onSuccess { leitura ->

                if (leitura == null) {
                    _state.value = GestaoRelatoriosUiState(
                        carregando = false,
                        acessoNegado = true
                    )
                } else {
                    ultimaLeituraMs = System.currentTimeMillis()
                    _state.value = leitura
                }

            }.onFailure { erro ->

                if (erro is CancellationException) {
                    throw erro
                }

                // Mantém os dados anteriores, se existirem.
                _state.value = _state.value.copy(
                    carregando = false,
                    atualizando = false,
                    erro = mensagemRelatorios(erro)
                )
            }
        }
    }

    /**
     * Lê as quatro tabelas em paralelo.
     * Equipamentos, empréstimos e defeitos são obrigatórios;
     * usuários são opcionais (sem eles os nomes ficam "não identificado").
     */
    private suspend fun lerTudo(): GestaoRelatoriosUiState =
        coroutineScope {

            val listaEquipamentos = async { equipamentos.listar() }
            val listaEmprestimos = async { emprestimos.listarGestao() }
            val listaDefeitos = async { defeitos.listarGestao() }

            val listaUsuarios = async {
                try {
                    usuarios.listar()
                } catch (erro: CancellationException) {
                    throw erro
                } catch (erro: Throwable) {
                    null
                }
            }

            val eq = listaEquipamentos.await()
            val us = listaUsuarios.await()

            val itensEmprestimos = montarEmprestimos(
                emprestimos = listaEmprestimos.await(),
                equipamentos = eq,
                usuarios = us
            )

            val itensOcorrencias = montarOcorrencias(
                defeitos = listaDefeitos.await(),
                equipamentos = eq,
                usuarios = us
            )

            GestaoRelatoriosUiState(
                carregando = false,
                atualizando = false,
                acessoNegado = false,
                erro = null,
                dados = DadosRelatorios(
                    equipamentos = eq,
                    emprestimos = itensEmprestimos,
                    ocorrencias = itensOcorrencias
                ),
                professoresIndisponiveis = us == null,
                atualizadoEm = LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            )
        }
}