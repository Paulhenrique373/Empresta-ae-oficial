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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.example.emprestaa.data.EmprestimoGestao
import com.example.emprestaa.data.FiltroEmprestimo
import com.example.emprestaa.data.GestaoEmprestimosViewModel
import com.example.emprestaa.data.ResumoEmprestimos
import com.example.emprestaa.data.filtrarEmprestimos
import com.example.emprestaa.data.historicoDevolucoes
import com.example.emprestaa.ui.theme.EmprestaCard
import com.example.emprestaa.ui.theme.EmprestaStatusBadge
import kotlinx.coroutines.delay

/** Atualização automática enquanto a tela está aberta (mesma regra aprovada para o painel: 30 segundos). */
private const val INTERVALO_EMPRESTIMOS_MS = 30_000L

private const val NAO_INFORMADO = "Não informado"

private fun corSituacaoEmprestimo(item: EmprestimoGestao, paleta: Paleta): Color = when {
    item.ativo -> paleta.azul
    item.devolvido -> paleta.verde
    else -> paleta.cinza
}

private data class NumeroResumo(val rotulo: String, val valor: Int)

/**
 * Empréstimos e devoluções de todos os professores (somente consulta para a Gestão). O acesso é validado no
 * servidor (usuario_e_gestao) e os dados continuam protegidos pela RLS. Não há ação de alterar registros.
 *
 * @param usuarioId id do usuário logado; separa o estado da tela entre contas diferentes no mesmo aparelho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestaoEmprestimos(
    usuarioId: String,
    onIr: (String) -> Unit,
    viewModel: GestaoEmprestimosViewModel = viewModel(key = "gestao-emprestimos-$usuarioId")
) {
    val estado by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Carrega ao abrir/voltar ao app e repete a cada 30 s somente enquanto a tela está visível.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.carregar()
            while (true) {
                delay(INTERVALO_EMPRESTIMOS_MS)
                viewModel.carregar(silencioso = true)
            }
        }
    }

    var pesquisa by rememberSaveable { mutableStateOf("") }
    var filtroNome by rememberSaveable { mutableStateOf(FiltroEmprestimo.TODOS.name) }
    var mostrarHistorico by rememberSaveable { mutableStateOf(false) }
    var detalheId by rememberSaveable { mutableStateOf<Long?>(null) }
    val paleta = rememberPaleta()
    val filtro = FiltroEmprestimo.entries.firstOrNull { it.name == filtroNome } ?: FiltroEmprestimo.TODOS

    val detalhe = detalheId?.let { id -> estado.itens.firstOrNull { it.id == id } }
    if (detalhe != null) {
        DetalheEmprestimoDialog(item = detalhe, cor = corSituacaoEmprestimo(detalhe, paleta), onFechar = { detalheId = null })
    }

    val ocupado = estado.carregando || estado.atualizando
    Scaffold(
        bottomBar = { BarraGestao(selecionada = "emprestimos", onIr = onIr) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Empréstimos e devoluções",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.carregar() }) {
                        if (ocupado) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp).semantics { contentDescription = "Atualizando os empréstimos" },
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar os empréstimos")
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
                    estado.acessoNegado -> MensagemEmprestimos(
                        titulo = "Acesso restrito à Gestão",
                        texto = "Esta conta não tem permissão de Gestão para consultar os empréstimos.",
                        botao = null,
                        onClick = {}
                    )
                    estado.carregando && resumo == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Carregando empréstimos…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    estado.erro != null && resumo == null -> MensagemEmprestimos(
                        titulo = "Não foi possível carregar os empréstimos",
                        texto = estado.erro ?: "Tente novamente.",
                        botao = "Tentar novamente",
                        onClick = { viewModel.carregar() }
                    )
                    resumo != null -> {
                        val base = remember(estado.itens, mostrarHistorico) {
                            if (mostrarHistorico) historicoDevolucoes(estado.itens) else estado.itens
                        }
                        val lista = remember(base, pesquisa, filtro, mostrarHistorico) {
                            filtrarEmprestimos(base, pesquisa, if (mostrarHistorico) FiltroEmprestimo.TODOS else filtro)
                        }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item { IndicadoresEmprestimos(resumo) }
                            item {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !mostrarHistorico,
                                        onClick = { mostrarHistorico = false },
                                        label = { Text("Todos os empréstimos") }
                                    )
                                    FilterChip(
                                        selected = mostrarHistorico,
                                        onClick = { mostrarHistorico = true },
                                        label = { Text("Histórico de devoluções (${resumo.devolvidos})") }
                                    )
                                }
                            }
                            item {
                                OutlinedTextField(
                                    value = pesquisa,
                                    onValueChange = { pesquisa = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Buscar por professor, etiqueta ou equipamento") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    singleLine = true
                                )
                            }
                            if (!mostrarHistorico) item {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FiltroEmprestimo.entries.forEach { f ->
                                        val quantidade = when (f) {
                                            FiltroEmprestimo.TODOS -> resumo.total
                                            FiltroEmprestimo.ATIVOS -> resumo.ativos
                                            FiltroEmprestimo.DEVOLVIDOS -> resumo.devolvidos
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
                                val vazioGeral = estado.itens.isEmpty()
                                Text(
                                    when {
                                        vazioGeral -> "Nenhum empréstimo registrado ainda."
                                        mostrarHistorico && resumo.devolvidos == 0 -> "Nenhuma devolução registrada ainda."
                                        else -> "Nenhum empréstimo encontrado com essa pesquisa ou filtro."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                )
                            }
                            items(lista, key = { it.id }) { emp ->
                                CartaoEmprestimo(
                                    item = emp,
                                    cor = corSituacaoEmprestimo(emp, paleta),
                                    historico = mostrarHistorico,
                                    onAbrir = { detalheId = emp.id }
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
private fun MensagemEmprestimos(titulo: String, texto: String, botao: String?, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (botao != null) Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(botao) }
        }
    }
}

@Composable
private fun IndicadoresEmprestimos(resumo: ResumoEmprestimos) {
    val numeros = listOf(
        NumeroResumo("Total de empréstimos", resumo.total),
        NumeroResumo("Empréstimos ativos", resumo.ativos),
        NumeroResumo("Empréstimos devolvidos", resumo.devolvidos),
        NumeroResumo("Equipamentos emprestados agora", resumo.equipamentosEmprestados),
        NumeroResumo("Professores com empréstimo ativo", resumo.professoresComEmprestimo)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        numeros.chunked(2).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                linha.forEach { numero -> CartaoNumero(numero, Modifier.weight(1f)) }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CartaoNumero(numero: NumeroResumo, modifier: Modifier) {
    EmprestaCard(modifier.semantics(mergeDescendants = true) { contentDescription = "${numero.rotulo}: ${numero.valor}" }) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(numero.valor.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(numero.rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CartaoEmprestimo(item: EmprestimoGestao, cor: Color, historico: Boolean, onAbrir: () -> Unit) {
    val professor = item.professor ?: "Professor não identificado"
    val equipamento = item.equipamento ?: "Equipamento não identificado"
    val devolucao = item.devolucaoTexto ?: if (item.ativo) "Ainda não devolvido" else NAO_INFORMADO
    EmprestaCard(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Ver detalhes do empréstimo", role = Role.Button, onClick = onAbrir)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(professor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(equipamento, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Etiqueta ${item.etiqueta ?: NAO_INFORMADO} · ${item.tipo ?: "Tipo não informado"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EmprestaStatusBadge(text = item.situacao, containerColor = cor.copy(alpha = .16f), contentColor = cor)
            }
            Text("Retirada: ${item.retiradaTexto ?: NAO_INFORMADO}", style = MaterialTheme.typography.bodySmall)
            // No histórico de devoluções a data de devolução é o dado principal; na lista geral aparece só quando existe ou está ativo.
            if (historico || item.devolucaoTexto != null || item.ativo) {
                Text("Devolução: $devolucao", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LinhaDetalhe(rotulo: String, valor: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetalheEmprestimoDialog(item: EmprestimoGestao, cor: Color, onFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Empréstimo nº ${item.id}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Situação", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EmprestaStatusBadge(text = item.situacao, containerColor = cor.copy(alpha = .16f), contentColor = cor)
                }
                LinhaDetalhe("Professor responsável", item.professor ?: NAO_INFORMADO)
                LinhaDetalhe("Equipamento", item.equipamento ?: NAO_INFORMADO)
                LinhaDetalhe("Número da etiqueta", item.etiqueta ?: NAO_INFORMADO)
                LinhaDetalhe("Tipo do equipamento", item.tipo ?: NAO_INFORMADO)
                LinhaDetalhe("Data da retirada", item.retiradaTexto ?: NAO_INFORMADO)
                LinhaDetalhe(
                    "Data da devolução",
                    item.devolucaoTexto ?: if (item.ativo) "Ainda não devolvido" else NAO_INFORMADO
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onFechar, colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())) { Text("Fechar") }
        }
    )
}