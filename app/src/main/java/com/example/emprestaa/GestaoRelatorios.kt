package com.example.emprestaa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emprestaa.data.CategoriaRelatorio
import com.example.emprestaa.data.GestaoRelatoriosViewModel
import com.example.emprestaa.data.ItemContagem
import com.example.emprestaa.data.PontoTempo
import com.example.emprestaa.data.RelatoriosPeriodo
import com.example.emprestaa.data.TipoPeriodo
import com.example.emprestaa.data.calcularIntervalo
import com.example.emprestaa.data.calcularRelatorios
import com.example.emprestaa.data.montarDocumento
import com.example.emprestaa.data.percentualTexto
import com.example.emprestaa.data.relatorioTemDados
import com.example.emprestaa.ui.theme.EmprestaCard
import com.example.emprestaa.ui.theme.EmprestaStatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private const val SEM_INFO = "Não informado"
private const val LIMITE_LISTA_TELA = 20

private data class IndicadorRelatorio(val rotulo: String, val valor: Int)

/** Cada bloco da lista vira uma única coluna (vários componentes no mesmo item da LazyColumn se sobreporiam). */
private fun androidx.compose.foundation.lazy.LazyListScope.bloco(
    conteudo: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    item { Column(Modifier.fillMaxWidth(), content = conteudo) }
}

/**
 * Relatórios administrativos da Gestão: geral, empréstimos, equipamentos e ocorrências.
 * Somente consulta. O acesso é validado no servidor (usuario_e_gestao) e os dados continuam
 * protegidos pela RLS. Dados históricos respeitam o período; a situação atual dos equipamentos não.
 *
 * @param usuarioId id do usuário logado; separa o estado da tela entre contas no mesmo aparelho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestaoRelatorios(
    usuarioId: String,
    onIr: (String) -> Unit,
    viewModel: GestaoRelatoriosViewModel = viewModel(key = "gestao-relatorios-$usuarioId")
) {
    val estado by viewModel.state.collectAsState()
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val paleta = rememberPaleta()
    val snackbar = remember { SnackbarHostState() }
    val avisosPersistentes = remember {
        AcessibilidadeStorage.carregar(contexto.getSharedPreferences("empresta-ae-local", 0)).avisosPersistentes
    }

    // Lê ao abrir/voltar ao app. Sem repetição automática; leituras recentes (< 60 s) são reaproveitadas.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.carregar()
        }
    }

    var categoriaNome by rememberSaveable { mutableStateOf(CategoriaRelatorio.GERAL.name) }
    var periodoNome by rememberSaveable { mutableStateOf(TipoPeriodo.TUDO.name) }
    var inicioDia by rememberSaveable { mutableStateOf<Long?>(null) }
    var fimDia by rememberSaveable { mutableStateOf<Long?>(null) }
    var escolhendoDatas by rememberSaveable { mutableStateOf(false) }
    var exportando by remember { mutableStateOf(false) }

    val categoria = CategoriaRelatorio.entries.firstOrNull { it.name == categoriaNome } ?: CategoriaRelatorio.GERAL
    val tipoPeriodo = TipoPeriodo.entries.firstOrNull { it.name == periodoNome } ?: TipoPeriodo.TUDO

    suspend fun avisar(mensagem: String) {
        snackbar.showSnackbar(
            message = mensagem,
            withDismissAction = avisosPersistentes,
            duration = if (avisosPersistentes) SnackbarDuration.Indefinite else SnackbarDuration.Short
        )
    }

    // O período é aplicado aqui, sobre os dados já lidos (sem nova consulta ao Supabase).
    val relatorio = remember(estado.dados, estado.atualizadoEm, tipoPeriodo, inicioDia, fimDia) {
        estado.dados?.let { dados ->
            val zona = ZoneId.systemDefault()
            calcularRelatorios(
                dados = dados,
                intervalo = calcularIntervalo(
                    tipo = tipoPeriodo,
                    hoje = LocalDate.now(zona),
                    zona = zona,
                    inicioPersonalizado = inicioDia?.let { LocalDate.ofEpochDay(it) },
                    fimPersonalizado = fimDia?.let { LocalDate.ofEpochDay(it) }
                ),
                zona = zona,
                professoresIndisponiveis = estado.professoresIndisponiveis
            )
        }
    }

    if (escolhendoDatas) {
        SeletorPeriodoDialog(
            inicio = inicioDia?.let { LocalDate.ofEpochDay(it) },
            fim = fimDia?.let { LocalDate.ofEpochDay(it) },
            onConfirmar = { ini, fim ->
                inicioDia = ini.toEpochDay()
                fimDia = fim.toEpochDay()
                periodoNome = TipoPeriodo.PERSONALIZADO.name
                escolhendoDatas = false
            },
            onCancelar = { escolhendoDatas = false }
        )
    }

    fun exportar(formato: FormatoExportacao) {
        val atual = relatorio ?: return
        if (exportando || !relatorioTemDados(categoria, atual)) return
        exportando = true
        escopo.launch {
            val documento = montarDocumento(categoria, atual)
            val resultado = runCatching {
                withContext(Dispatchers.IO) {
                    gerarArquivoRelatorio(contexto, documento, formato, categoria.arquivo)
                }
            }
            resultado
                .onSuccess { arquivo ->
                    runCatching { compartilharArquivoRelatorio(contexto, arquivo, formato, documento.titulo) }
                        .onFailure { avisar("Arquivo ${formato.rotulo} gerado, mas não foi possível abrir o compartilhamento.") }
                }
                .onFailure { avisar("Não foi possível gerar o arquivo ${formato.rotulo}.") }
            exportando = false
        }
    }

    val ocupado = estado.carregando || estado.atualizando
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BarraGestao(selecionada = "relatorios", onIr = onIr) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Relatórios",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.carregar(forcar = true) }) {
                        if (ocupado) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp).semantics { contentDescription = "Atualizando os relatórios" },
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar os relatórios")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxHeight().align(Alignment.TopCenter)) {
                when {
                    estado.acessoNegado -> MensagemRelatorios(
                        titulo = "Acesso restrito à Gestão",
                        texto = "Esta conta não tem permissão de Gestão para consultar os relatórios.",
                        botao = null,
                        onClick = {}
                    )
                    estado.carregando && estado.dados == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Carregando relatórios…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    estado.erro != null && estado.dados == null -> MensagemRelatorios(
                        titulo = "Não foi possível carregar os relatórios",
                        texto = estado.erro ?: "Tente novamente.",
                        botao = "Tentar novamente",
                        onClick = { viewModel.carregar(forcar = true) }
                    )
                    relatorio != null -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        bloco {
                            Text(
                                "Acompanhe e analise os dados da escola. Dados históricos seguem o período escolhido; a situação atual dos equipamentos não depende dele.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        bloco {
                            RotuloSecao("Relatório")
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CategoriaRelatorio.entries.forEach { c ->
                                    FilterChip(selected = categoria == c, onClick = { categoriaNome = c.name }, label = { Text(c.rotulo) })
                                }
                            }
                        }
                        if (categoria != CategoriaRelatorio.EQUIPAMENTOS) {
                            bloco {
                                RotuloSecao("Período")
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TipoPeriodo.entries.forEach { t ->
                                        FilterChip(
                                            selected = tipoPeriodo == t,
                                            onClick = {
                                                if (t == TipoPeriodo.PERSONALIZADO) {
                                                    escolhendoDatas = true
                                                } else {
                                                    periodoNome = t.name
                                                }
                                            },
                                            label = { Text(t.rotulo) }
                                        )
                                    }
                                }
                            }
                            bloco {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Período aplicado: ${relatorio.intervalo.descricao}",
                                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (tipoPeriodo == TipoPeriodo.PERSONALIZADO) {
                                        TextButton(onClick = { escolhendoDatas = true }, colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())) {
                                            Text("Alterar datas")
                                        }
                                    }
                                }
                            }
                        } else {
                            bloco {
                                Text(
                                    "Este relatório mostra a situação atual dos equipamentos; o período não se aplica.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        bloco {
                            val temDados = relatorioTemDados(categoria, relatorio)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FormatoExportacao.entries.forEach { formato ->
                                    OutlinedButton(
                                        onClick = { exportar(formato) },
                                        enabled = temDados && !exportando,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) { Text(if (exportando) "Gerando…" else "Exportar ${formato.rotulo}") }
                                }
                            }
                            if (!temDados) {
                                Text(
                                    "Não há dados neste relatório para exportar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        if (estado.erro != null) bloco {
                            Row(
                                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Não foi possível atualizar. ${estado.erro}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = { viewModel.carregar(forcar = true) }, colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())) {
                                    Text("Tentar novamente")
                                }
                            }
                        }
                        if (relatorio.professoresIndisponiveis) bloco {
                            Text(
                                "Os nomes dos professores não puderam ser lidos; aparecem como não identificados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (estado.atualizadoEm != null) bloco {
                            Text(
                                "Dados lidos às ${estado.atualizadoEm} · use o botão de atualizar para ler de novo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        when (categoria) {
                            CategoriaRelatorio.GERAL -> conteudoGeral(relatorio, paleta)
                            CategoriaRelatorio.EMPRESTIMOS -> conteudoEmprestimos(relatorio, paleta)
                            CategoriaRelatorio.EQUIPAMENTOS -> conteudoEquipamentos(relatorio, paleta)
                            CategoriaRelatorio.OCORRENCIAS -> conteudoOcorrencias(relatorio, paleta)
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Conteúdo de cada relatório                                           */
/* ------------------------------------------------------------------ */

private fun androidx.compose.foundation.lazy.LazyListScope.conteudoGeral(r: RelatoriosPeriodo, paleta: Paleta) {
    val eq = r.equipamentos
    val em = r.emprestimos
    val oc = r.ocorrencias

    bloco {
        TituloBloco("Equipamentos — situação atual", "Não depende do período escolhido.")
        Indicadores(
            buildList {
                add(IndicadorRelatorio("Total de equipamentos", eq.total))
                add(IndicadorRelatorio("Disponíveis", eq.disponiveis))
                add(IndicadorRelatorio("Em uso", eq.emUso))
                add(IndicadorRelatorio("Em manutenção", eq.manutencao))
                if (eq.outras > 0) add(IndicadorRelatorio("Outras situações", eq.outras))
            }
        )
    }
    bloco {
        TituloBloco("Empréstimos — retirados no período", r.intervalo.descricao)
        Indicadores(
            listOf(
                IndicadorRelatorio("Total de empréstimos", em.retiradas),
                IndicadorRelatorio("Ativos", em.ativos),
                IndicadorRelatorio("Devolvidos", em.devolvidos)
            )
        )
        NotaSemData(em.semData)
    }
    bloco {
        TituloBloco("Ocorrências — registradas no período", r.intervalo.descricao)
        Indicadores(
            listOf(
                IndicadorRelatorio("Total de ocorrências", oc.total),
                IndicadorRelatorio("Pendentes", oc.pendentes),
                IndicadorRelatorio("Em análise", oc.emAnalise),
                IndicadorRelatorio("Resolvidas", oc.resolvidas)
            )
        )
        NotaSemData(oc.semData)
        Text(
            "Pendentes = Registrado · Em análise = Em manutenção · Resolvidas = Consertado",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    if (eq.total > 0) bloco {
        CartaoGrafico("Equipamentos por situação (atual)", "Quantidade e percentual do total de equipamentos.") {
            GraficoBarras(eq.porSituacao, paleta, mostrarPercentual = true)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.conteudoEmprestimos(r: RelatoriosPeriodo, paleta: Paleta) {
    val em = r.emprestimos

    bloco {
        TituloBloco("Empréstimos", r.intervalo.descricao)
        Indicadores(
            listOf(
                IndicadorRelatorio("Retirados no período", em.retiradas),
                IndicadorRelatorio("Devoluções realizadas no período", em.devolucoesRealizadas),
                IndicadorRelatorio("Ativos (entre os retirados no período)", em.ativos),
                IndicadorRelatorio("Ativos agora (atual, sem período)", em.ativosAgora)
            )
        )
        NotaSemData(em.semData)
    }
    bloco {
        CartaoGrafico("Empréstimos por ${em.serieAgrupamento}", "Retiradas registradas em cada ${em.serieAgrupamento} do período.") {
            GraficoColunas(em.serie, em.serieAgrupamento, paleta)
        }
    }
    bloco {
        CartaoGrafico("Equipamentos mais utilizados", "Até 5 equipamentos com mais retiradas no período.") {
            GraficoBarras(em.maisUtilizados, paleta, mostrarPercentual = false, textoVazio = "Nenhum empréstimo no período.")
        }
    }
    bloco {
        CartaoGrafico(
            "Professores com empréstimos",
            if (em.professoresTotal > 0) "${em.professoresTotal} professor(es) com empréstimo no período; mostrando até 10." else "Professores com empréstimo no período."
        ) {
            GraficoBarras(em.professores, paleta, mostrarPercentual = false, textoVazio = "Nenhum professor com empréstimo no período.")
        }
    }
    bloco {
        TituloBloco(
            "Histórico de empréstimos",
            if (em.historico.size > LIMITE_LISTA_TELA) "Mostrando os $LIMITE_LISTA_TELA mais recentes de ${em.historico.size}. A exportação inclui todos."
            else "${em.historico.size} registro(s) no período."
        )
        if (em.historico.isEmpty()) TextoVazio("Nenhum empréstimo no período.")
    }
    items(em.historico.take(LIMITE_LISTA_TELA).size) { indice ->
        val e = em.historico[indice]
        val cor = if (e.ativo) paleta.azul else if (e.devolvido) paleta.verde else paleta.cinza
        EmprestaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.professor ?: "Professor não identificado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${e.equipamento ?: "Equipamento não identificado"} · etiqueta ${e.etiqueta ?: SEM_INFO}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    EmprestaStatusBadge(text = e.situacao, containerColor = cor.copy(alpha = .16f), contentColor = cor)
                }
                Text("Retirada: ${e.retiradaTexto ?: SEM_INFO}", style = MaterialTheme.typography.bodySmall)
                Text("Devolução: ${e.devolucaoTexto ?: if (e.ativo) "Ainda não devolvido" else SEM_INFO}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.conteudoEquipamentos(r: RelatoriosPeriodo, paleta: Paleta) {
    val eq = r.equipamentos

    bloco {
        TituloBloco("Equipamentos — situação atual", "Reflete o momento da leitura dos dados.")
        Indicadores(
            buildList {
                add(IndicadorRelatorio("Total de equipamentos", eq.total))
                add(IndicadorRelatorio("Disponíveis", eq.disponiveis))
                add(IndicadorRelatorio("Emprestados (em uso)", eq.emUso))
                add(IndicadorRelatorio("Em manutenção", eq.manutencao))
                if (eq.outras > 0) add(IndicadorRelatorio("Outras situações", eq.outras))
            }
        )
    }
    if (eq.total == 0) {
        bloco { TextoVazio("Nenhum equipamento cadastrado.") }
        return
    }
    bloco {
        CartaoGrafico("Equipamentos por situação", "Quantidade e percentual do total.") {
            GraficoBarras(eq.porSituacao, paleta, mostrarPercentual = true)
        }
    }
    bloco {
        CartaoGrafico("Equipamentos por tipo", "Quantidade e percentual do total.") {
            GraficoBarras(eq.porTipo, paleta, mostrarPercentual = true)
        }
    }
    bloco {
        CartaoGrafico("Equipamentos por marca", "Até 6 marcas; as demais ficam em “Outras”.") {
            if (eq.porMarca.size >= 2) {
                GraficoBarras(eq.porMarca, paleta, mostrarPercentual = true)
            } else {
                TextoVazio("Há apenas uma marca cadastrada; não há distribuição para mostrar.")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.conteudoOcorrencias(r: RelatoriosPeriodo, paleta: Paleta) {
    val oc = r.ocorrencias

    bloco {
        TituloBloco("Ocorrências e defeitos", r.intervalo.descricao)
        Indicadores(
            listOf(
                IndicadorRelatorio("Total de ocorrências", oc.total),
                IndicadorRelatorio("Pendentes", oc.pendentes),
                IndicadorRelatorio("Em análise", oc.emAnalise),
                IndicadorRelatorio("Resolvidas", oc.resolvidas)
            )
        )
        NotaSemData(oc.semData)
        Text(
            "Pendentes = Registrado · Em análise = Em manutenção · Resolvidas = Consertado",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    if (oc.total == 0) {
        bloco { TextoVazio("Nenhuma ocorrência no período.") }
        return
    }
    bloco {
        CartaoGrafico("Ocorrências por situação", "Situação registrada no banco; percentual sobre o total do período.") {
            GraficoBarras(oc.porSituacao, paleta, mostrarPercentual = true)
        }
    }
    bloco {
        CartaoGrafico("Ocorrências por tipo de defeito", "Até 6 tipos; os demais ficam em “Outras”.") {
            GraficoBarras(oc.porTipo, paleta, mostrarPercentual = true)
        }
    }
    bloco {
        val maior = oc.equipamentosMaisOcorrencias.firstOrNull()?.valor ?: 0
        CartaoGrafico("Equipamentos com mais ocorrências", "Até 5 equipamentos no período.") {
            if (maior >= 2) {
                GraficoBarras(oc.equipamentosMaisOcorrencias, paleta, mostrarPercentual = false)
            } else {
                TextoVazio("Nenhum equipamento teve mais de uma ocorrência no período.")
            }
        }
    }
    bloco {
        CartaoGrafico("Ocorrências por ${oc.serieAgrupamento}", "Registros em cada ${oc.serieAgrupamento} do período.") {
            GraficoColunas(oc.serie, oc.serieAgrupamento, paleta)
        }
    }
    bloco {
        TituloBloco(
            "Registros de ocorrências",
            if (oc.registros.size > LIMITE_LISTA_TELA) "Mostrando os $LIMITE_LISTA_TELA mais recentes de ${oc.registros.size}. A exportação inclui todos."
            else "${oc.registros.size} registro(s) no período."
        )
    }
    items(oc.registros.take(LIMITE_LISTA_TELA).size) { indice ->
        val o = oc.registros[indice]
        val cor = when (o.categoria) {
            com.example.emprestaa.data.CategoriaOcorrencia.PENDENTE -> paleta.laranja
            com.example.emprestaa.data.CategoriaOcorrencia.EM_ANALISE -> paleta.azul
            com.example.emprestaa.data.CategoriaOcorrencia.RESOLVIDA -> paleta.verde
        }
        EmprestaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(o.tipoDefeito ?: "Tipo de defeito não informado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${o.equipamento ?: "Equipamento não identificado"} · etiqueta ${o.etiqueta ?: SEM_INFO}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    EmprestaStatusBadge(text = o.situacao, containerColor = cor.copy(alpha = .16f), contentColor = cor)
                }
                Text("Professor: ${o.professor ?: SEM_INFO}", style = MaterialTheme.typography.bodySmall)
                Text("Registrada em: ${o.dataTexto ?: SEM_INFO}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Peças de interface                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun MensagemRelatorios(titulo: String, texto: String, botao: String?, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (botao != null) Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(botao) }
        }
    }
}

@Composable
private fun RotuloSecao(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun TituloBloco(titulo: String, apoio: String?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        if (apoio != null) Text(apoio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TextoVazio(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
private fun NotaSemData(quantidade: Int) {
    if (quantidade > 0) {
        Text(
            "$quantidade registro(s) sem data ficaram fora deste período.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun Indicadores(numeros: List<IndicadorRelatorio>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        numeros.chunked(2).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                linha.forEach { numero ->
                    EmprestaCard(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "${numero.rotulo}: ${numero.valor}" }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(numero.valor.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(numero.rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CartaoGrafico(titulo: String, legenda: String, conteudo: @Composable () -> Unit) {
    EmprestaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(legenda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            conteudo()
        }
    }
}

/** Barras horizontais: cada linha mostra o rótulo (legenda), o valor e, se pedido, o percentual do total do gráfico. */
@Composable
private fun GraficoBarras(
    itens: List<ItemContagem>,
    paleta: Paleta,
    mostrarPercentual: Boolean,
    textoVazio: String = "Sem dados para mostrar."
) {
    val total = itens.sumOf { it.valor }

    if (itens.isEmpty() || total == 0) {
        TextoVazio(textoVazio)
        return
    }

    val maximo = itens.maxOf { it.valor }.coerceAtLeast(1)
    val trilho = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itens.forEachIndexed { indice, item ->
            val cor = paleta.serie[indice % paleta.serie.size]
            val percentual = if (mostrarPercentual) " (${percentualTexto(item.valor, total)})" else ""

            Column(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${item.rotulo}: ${item.valor}$percentual" },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.rotulo, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${item.valor}$percentual", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val raio = CornerRadius(7.dp.toPx())
                    drawRoundRect(color = trilho, size = size, cornerRadius = raio)
                    if (item.valor > 0) {
                        val largura = (size.width * item.valor / maximo).coerceAtLeast(14.dp.toPx())
                        drawRoundRect(color = cor, size = Size(largura, size.height), cornerRadius = raio)
                    }
                }
            }
        }
    }
}

/** Colunas por dia ou mês. Mostra o maior valor, a primeira e a última data. Sem dados, mostra uma explicação. */
@Composable
private fun GraficoColunas(serie: List<PontoTempo>, agrupamento: String, paleta: Paleta) {
    val total = serie.sumOf { it.valor }

    if (serie.isEmpty() || total == 0) {
        TextoVazio("Sem registros com data no período para montar o gráfico.")
        return
    }

    val maximo = serie.maxOf { it.valor }.coerceAtLeast(1)
    val pico = serie.first { it.valor == maximo }
    val cor = paleta.azul
    val base = MaterialTheme.colorScheme.outline
    val descricao = "Total $total. Maior valor: $maximo em ${pico.rotulo}. " +
            serie.filter { it.valor > 0 }.joinToString(". ") { "${it.rotulo}: ${it.valor}" }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Total no período: $total · Maior valor: $maximo (${pico.rotulo})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

        Canvas(Modifier.fillMaxWidth().height(140.dp).clearAndSetSemantics { contentDescription = descricao }) {
            val n = serie.size
            val passo = size.width / n
            val largura = passo * 0.7f
            val altura = size.height - 2.dp.toPx()

            drawLine(base, Offset(0f, altura), Offset(size.width, altura), strokeWidth = 1.dp.toPx())

            serie.forEachIndexed { i, ponto ->
                if (ponto.valor > 0) {
                    val h = (altura * ponto.valor / maximo).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = cor,
                        topLeft = Offset(i * passo + (passo - largura) / 2f, altura - h),
                        size = Size(largura, h),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(serie.first().rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("cada coluna = 1 $agrupamento", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(serie.last().rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Seleção de data inicial e final. As datas do seletor vêm em UTC; convertidas para LocalDate sem deslocar o dia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorPeriodoDialog(
    inicio: LocalDate?,
    fim: LocalDate?,
    onConfirmar: (LocalDate, LocalDate) -> Unit,
    onCancelar: () -> Unit
) {
    fun paraMs(data: LocalDate?): Long? =
        data?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    fun paraData(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

    val seletor = rememberDateRangePickerState(
        initialSelectedStartDateMillis = paraMs(inicio),
        initialSelectedEndDateMillis = paraMs(fim)
    )

    val msInicio = seletor.selectedStartDateMillis
    val msFim = seletor.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onCancelar,
        confirmButton = {
            TextButton(
                enabled = msInicio != null,
                onClick = {
                    if (msInicio != null) {
                        onConfirmar(paraData(msInicio), paraData(msFim ?: msInicio))
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())
            ) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())) { Text("Cancelar") }
        }
    ) {
        DateRangePicker(state = seletor, modifier = Modifier.height(500.dp))
    }
}