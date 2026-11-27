package com.example.emprestaa

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import com.example.emprestaa.data.CategoriaOcorrencia
import com.example.emprestaa.data.FiltroOcorrencia
import com.example.emprestaa.data.GestaoOcorrenciasViewModel
import com.example.emprestaa.data.OcorrenciaGestao
import com.example.emprestaa.data.ResumoOcorrencias
import com.example.emprestaa.data.SITUACOES_OCORRENCIA
import com.example.emprestaa.data.filtrarOcorrencias
import com.example.emprestaa.data.normalizarTexto
import com.example.emprestaa.ui.theme.EmprestaCard
import com.example.emprestaa.ui.theme.EmprestaStatusBadge
import kotlinx.coroutines.delay

/** Atualização automática enquanto a tela está aberta (mesma regra das outras telas da Gestão: 30 segundos). */
private const val INTERVALO_OCORRENCIAS_MS = 30_000L

private const val SEM_INFORMACAO = "Não informado"

private fun corCategoria(categoria: CategoriaOcorrencia, paleta: Paleta): Color = when (categoria) {
    CategoriaOcorrencia.PENDENTE -> paleta.laranja
    CategoriaOcorrencia.EM_ANALISE -> paleta.azul
    CategoriaOcorrencia.RESOLVIDA -> paleta.verde
}

private data class IndicadorOcorrencia(val rotulo: String, val valor: Int)

/**
 * Ocorrências e defeitos registrados pelos professores, para a Gestão acompanhar e atualizar a situação.
 * O acesso é validado no servidor (usuario_e_gestao) e os dados continuam protegidos pela RLS.
 *
 * @param usuarioId id do usuário logado; separa o estado da tela entre contas diferentes no mesmo aparelho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestaoOcorrencias(
    usuarioId: String,
    onIr: (String) -> Unit,
    viewModel: GestaoOcorrenciasViewModel = viewModel(key = "gestao-ocorrencias-$usuarioId")
) {
    val estado by viewModel.state.collectAsState()
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val avisosPersistentes = remember {
        AcessibilidadeStorage.carregar(contexto.getSharedPreferences("empresta-ae-local", 0)).avisosPersistentes
    }
    val snackbar = remember { SnackbarHostState() }

    // Carrega ao abrir/voltar ao app e repete a cada 30 s somente enquanto a tela está visível.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.carregar()
            while (true) {
                delay(INTERVALO_OCORRENCIAS_MS)
                viewModel.carregar(silencioso = true)
            }
        }
    }

    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            snackbar.showSnackbar(
                message = it,
                withDismissAction = avisosPersistentes,
                duration = if (avisosPersistentes) SnackbarDuration.Indefinite else SnackbarDuration.Short
            )
            viewModel.consumirAviso()
        }
    }

    var pesquisa by rememberSaveable { mutableStateOf("") }
    var filtroNome by rememberSaveable { mutableStateOf(FiltroOcorrencia.TODAS.name) }
    var detalheId by rememberSaveable { mutableStateOf<Long?>(null) }
    var situacaoEscolhida by rememberSaveable { mutableStateOf<String?>(null) }
    val paleta = rememberPaleta()
    val filtro = FiltroOcorrencia.entries.firstOrNull { it.name == filtroNome } ?: FiltroOcorrencia.TODAS

    val detalhe = detalheId?.let { id -> estado.itens.firstOrNull { it.id == id } }
    if (detalhe != null) {
        DetalheOcorrenciaDialog(
            item = detalhe,
            cor = corCategoria(detalhe.categoria, paleta),
            salvando = estado.salvando,
            erro = estado.erroStatus,
            onEscolher = { nova -> situacaoEscolhida = nova },
            onFechar = {
                if (!estado.salvando) {
                    viewModel.limparErroStatus()
                    situacaoEscolhida = null
                    detalheId = null
                }
            }
        )
        val nova = situacaoEscolhida
        if (nova != null) {
            AlertDialog(
                onDismissRequest = { situacaoEscolhida = null },
                title = { Text("Alterar situação?") },
                text = {
                    Text("A ocorrência nº ${detalhe.id} passará de “${detalhe.situacao}” para “$nova”.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            situacaoEscolhida = null
                            viewModel.alterarSituacao(detalhe.id, nova)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())
                    ) { Text("Confirmar") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { situacaoEscolhida = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())
                    ) { Text("Cancelar") }
                }
            )
        }
    }

    val ocupado = estado.carregando || estado.atualizando
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BarraGestao(selecionada = "ocorrencias", onIr = onIr) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ocorrências e defeitos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.carregar() }, enabled = !estado.salvando) {
                        if (ocupado) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp).semantics { contentDescription = "Atualizando as ocorrências" },
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar as ocorrências")
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
                val resumo = estado.resumo
                when {
                    estado.acessoNegado -> MensagemOcorrencias(
                        titulo = "Acesso restrito à Gestão",
                        texto = "Esta conta não tem permissão de Gestão para consultar as ocorrências.",
                        botao = null,
                        onClick = {}
                    )
                    estado.carregando && resumo == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Carregando ocorrências…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    estado.erro != null && resumo == null -> MensagemOcorrencias(
                        titulo = "Não foi possível carregar as ocorrências",
                        texto = estado.erro ?: "Tente novamente.",
                        botao = "Tentar novamente",
                        onClick = { viewModel.carregar() }
                    )
                    resumo != null -> {
                        val lista = remember(estado.itens, pesquisa, filtro) {
                            filtrarOcorrencias(estado.itens, pesquisa, filtro)
                        }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text(
                                    "Problemas registrados pelos professores nos equipamentos da escola. Toque em uma ocorrência para ver os detalhes e atualizar a situação.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item { IndicadoresOcorrencias(resumo) }
                            item {
                                Text(
                                    "Pendentes = Registrado · Em análise = Em manutenção · Resolvidas = Consertado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item {
                                OutlinedTextField(
                                    value = pesquisa,
                                    onValueChange = { pesquisa = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Buscar por equipamento, etiqueta, professor ou defeito") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    singleLine = true
                                )
                            }
                            item {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FiltroOcorrencia.entries.forEach { f ->
                                        val quantidade = when (f) {
                                            FiltroOcorrencia.TODAS -> resumo.total
                                            FiltroOcorrencia.PENDENTES -> resumo.pendentes
                                            FiltroOcorrencia.EM_ANALISE -> resumo.emAnalise
                                            FiltroOcorrencia.RESOLVIDAS -> resumo.resolvidas
                                        }
                                        FilterChip(
                                            selected = filtro == f,
                                            onClick = { filtroNome = f.name },
                                            label = { Text("${f.rotulo} ($quantidade)") }
                                        )
                                    }
                                }
                            }
                            if (estado.erro != null) item {
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
                                    TextButton(onClick = { viewModel.carregar() }, colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())) {
                                        Text("Tentar novamente")
                                    }
                                }
                            }
                            if (estado.professoresIndisponiveis) item {
                                Text(
                                    "Os nomes dos professores não puderam ser lidos nesta atualização.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (estado.equipamentosIndisponiveis) item {
                                Text(
                                    "Os dados dos equipamentos não puderam ser lidos nesta atualização.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (estado.atualizadoEm != null) item {
                                Text(
                                    "Atualizado às ${estado.atualizadoEm} · atualiza sozinho a cada 30 segundos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (lista.isEmpty()) item {
                                Text(
                                    if (estado.itens.isEmpty()) "Nenhuma ocorrência registrada ainda."
                                    else "Nenhuma ocorrência encontrada com essa pesquisa ou filtro.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                )
                            }
                            items(lista, key = { it.id }) { ocorrencia ->
                                CartaoOcorrencia(
                                    item = ocorrencia,
                                    cor = corCategoria(ocorrencia.categoria, paleta),
                                    onAbrir = {
                                        viewModel.limparErroStatus()
                                        detalheId = ocorrencia.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MensagemOcorrencias(titulo: String, texto: String, botao: String?, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (botao != null) Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(botao) }
        }
    }
}

@Composable
private fun IndicadoresOcorrencias(resumo: ResumoOcorrencias) {
    val numeros = listOf(
        IndicadorOcorrencia("Total de ocorrências", resumo.total),
        IndicadorOcorrencia("Pendentes", resumo.pendentes),
        IndicadorOcorrencia("Em análise", resumo.emAnalise),
        IndicadorOcorrencia("Resolvidas", resumo.resolvidas),
        IndicadorOcorrencia("Equipamentos com problemas", resumo.equipamentosComProblema)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        numeros.chunked(2).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                linha.forEach { numero -> CartaoIndicador(numero, Modifier.weight(1f)) }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CartaoIndicador(numero: IndicadorOcorrencia, modifier: Modifier) {
    EmprestaCard(modifier.semantics(mergeDescendants = true) { contentDescription = "${numero.rotulo}: ${numero.valor}" }) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(numero.valor.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(numero.rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CartaoOcorrencia(item: OcorrenciaGestao, cor: Color, onAbrir: () -> Unit) {
    val equipamento = item.equipamento ?: "Equipamento não identificado"
    val professor = item.professor ?: "Professor não identificado"
    EmprestaCard(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Ver detalhes da ocorrência", role = Role.Button, onClick = onAbrir)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ocorrência nº ${item.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.tipoDefeito ?: "Tipo de defeito não informado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(equipamento, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Etiqueta ${item.etiqueta ?: SEM_INFORMACAO} · ${item.tipoEquipamento ?: "Tipo não informado"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EmprestaStatusBadge(text = item.situacao, containerColor = cor.copy(alpha = .16f), contentColor = cor)
            }
            Text(
                item.descricao ?: "Sem descrição informada.",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text("Professor: $professor", style = MaterialTheme.typography.bodySmall)
            Text("Registrada em: ${item.dataTexto ?: SEM_INFORMACAO}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LinhaDetalheOcorrencia(rotulo: String, valor: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetalheOcorrenciaDialog(
    item: OcorrenciaGestao,
    cor: Color,
    salvando: Boolean,
    erro: String?,
    onEscolher: (String) -> Unit,
    onFechar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Ocorrência nº ${item.id}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Situação", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EmprestaStatusBadge(text = item.situacao, containerColor = cor.copy(alpha = .16f), contentColor = cor)
                }
                LinhaDetalheOcorrencia("Equipamento", item.equipamento ?: SEM_INFORMACAO)
                LinhaDetalheOcorrencia("Número da etiqueta", item.etiqueta ?: SEM_INFORMACAO)
                LinhaDetalheOcorrencia("Tipo do equipamento", item.tipoEquipamento ?: SEM_INFORMACAO)
                LinhaDetalheOcorrencia("Professor responsável", item.professor ?: SEM_INFORMACAO)
                LinhaDetalheOcorrencia("Tipo do defeito", item.tipoDefeito ?: SEM_INFORMACAO)
                LinhaDetalheOcorrencia("Descrição", item.descricao ?: SEM_INFORMACAO)
                LinhaDetalheOcorrencia("Data do registro", item.dataTexto ?: SEM_INFORMACAO)

                Text(
                    "Alterar situação da ocorrência",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                SITUACOES_OCORRENCIA
                    .filter { normalizarTexto(it) != normalizarTexto(item.situacao) }
                    .forEach { situacao ->
                        OutlinedButton(
                            onClick = { onEscolher(situacao) },
                            enabled = !salvando,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Marcar como $situacao") }
                    }
                if (salvando) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Salvando…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (erro != null) {
                    Text(
                        erro,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Text(
                    "Esta tela altera somente a situação da ocorrência.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onFechar,
                enabled = !salvando,
                colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())
            ) { Text("Fechar") }
        }
    )
}