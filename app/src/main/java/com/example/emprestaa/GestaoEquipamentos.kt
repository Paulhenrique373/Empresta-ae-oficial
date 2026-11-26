package com.example.emprestaa

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emprestaa.data.EquipamentoRow
import com.example.emprestaa.data.FormularioEquipamento
import com.example.emprestaa.data.GestaoEquipamentosViewModel
import com.example.emprestaa.data.normalizarTexto
import com.example.emprestaa.ui.theme.EmprestaCard
import com.example.emprestaa.ui.theme.EmprestaStatusBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FILTROS_SITUACAO = listOf("Todos", "Disponível", "Em uso", "Manutenção")
private const val INTERVALO_AUTOMATICO_MS = 30_000L

private fun corSituacao(status: String, paleta: Paleta): Color = when (normalizarTexto(status)) {
    "disponivel" -> paleta.verde
    "em uso" -> paleta.azul
    "manutencao" -> paleta.laranja
    else -> paleta.cinza
}

/**
 * Gestão de equipamentos: lista com busca e filtro, cadastro, edição, mudança de situação
 * (Disponível/Manutenção) e geração de etiqueta. As gravações são validadas no servidor e protegidas pela RLS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestaoEquipamentos(
    usuarioId: String,
    onIr: (String) -> Unit,
    viewModel: GestaoEquipamentosViewModel = viewModel(key = "gestao-equipamentos-$usuarioId")
) {
    val estado by viewModel.state.collectAsState()
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val avisosPersistentes = remember {
        AcessibilidadeStorage.carregar(contexto.getSharedPreferences("empresta-ae-local", 0)).avisosPersistentes
    }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Carrega ao abrir/voltar ao app e repete a cada 30 s enquanto a tela está visível.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.carregar()
            while (true) {
                delay(INTERVALO_AUTOMATICO_MS)
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
    var filtro by rememberSaveable { mutableStateOf("Todos") }
    var criando by rememberSaveable { mutableStateOf(false) }
    var editandoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var etiquetaId by rememberSaveable { mutableStateOf<Long?>(null) }
    val paleta = rememberPaleta()

    val editando = editandoId?.let { id -> estado.itens.firstOrNull { it.id == id } }
    if (criando || editando != null) {
        FormularioEquipamentoDialog(
            editando = editando,
            salvando = estado.salvando,
            erro = estado.erroSalvar,
            onSalvar = { form -> viewModel.salvar(editando, form) { criando = false; editandoId = null } },
            onCancelar = { criando = false; editandoId = null; viewModel.limparErroSalvar() }
        )
    }

    val paraEtiqueta = etiquetaId?.let { id -> estado.itens.firstOrNull { it.id == id } }
    if (paraEtiqueta != null) {
        val equipamento = Equipamento(
            id = paraEtiqueta.id.toString(),
            codigo = paraEtiqueta.etiqueta,
            nome = "${paraEtiqueta.marca} ${paraEtiqueta.modelo}",
            tipo = paraEtiqueta.tipo,
            status = paraEtiqueta.status
        )
        EquipmentLabelDialog(
            equipment = equipamento,
            onDismiss = { etiquetaId = null },
            onShare = { label ->
                runCatching { shareEquipmentLabel(contexto, equipamento, label) }
                    .onSuccess { etiquetaId = null }
                    .onFailure { erro -> escopo.launch { snackbar.showSnackbar(erro.localizedMessage ?: "Não foi possível compartilhar a etiqueta.") } }
            },
            onPrint = { label ->
                runCatching { printEquipmentLabel(contexto, equipamento, label) }
                    .onSuccess { etiquetaId = null }
                    .onFailure { erro -> escopo.launch { snackbar.showSnackbar(erro.localizedMessage ?: "Não foi possível abrir a impressão.") } }
            }
        )
    }

    val ocupado = estado.carregando || estado.atualizando
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BarraGestao(selecionada = "equipamentos", onIr = onIr) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.limparErroSalvar(); criando = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo equipamento") }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Equipamentos",
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
                                modifier = Modifier.size(22.dp).semantics { contentDescription = "Atualizando a lista" },
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar a lista")
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
                val itens = estado.itens
                fun contar(situacao: String) =
                    if (situacao == "Todos") itens.size else itens.count { normalizarTexto(it.status) == normalizarTexto(situacao) }

                when {
                    estado.carregando && itens.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Carregando equipamentos…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    estado.erro != null && itens.isEmpty() -> MensagemEquipamentos(
                        titulo = "Não foi possível carregar os equipamentos",
                        texto = estado.erro ?: "Tente novamente.",
                        botao = "Tentar novamente",
                        onClick = { viewModel.carregar() }
                    )
                    itens.isEmpty() -> MensagemEquipamentos(
                        titulo = "Nenhum equipamento cadastrado",
                        texto = "Toque em Novo equipamento para cadastrar o primeiro.",
                        botao = null,
                        onClick = {}
                    )
                    else -> {
                        val consulta = pesquisa.trim()
                        val lista = itens
                            .filter { eq ->
                                (filtro == "Todos" || normalizarTexto(eq.status) == normalizarTexto(filtro)) &&
                                        (consulta.isEmpty() || "${eq.etiqueta} ${eq.marca} ${eq.modelo} ${eq.tipo} ${eq.status}".contains(consulta, ignoreCase = true))
                            }
                            .sortedBy { it.etiqueta }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                OutlinedTextField(
                                    value = pesquisa,
                                    onValueChange = { pesquisa = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Buscar por etiqueta, marca, modelo ou tipo") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    singleLine = true
                                )
                            }
                            item {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FILTROS_SITUACAO.forEach { f ->
                                        FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text("$f (${contar(f)})") })
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
                                    TextButton(onClick = { viewModel.carregar() }) { Text("Tentar novamente") }
                                }
                            }
                            if (lista.isEmpty()) item {
                                Text("Nenhum equipamento encontrado com esse filtro.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(lista, key = { it.id }) { eq ->
                                CartaoEquipamento(
                                    eq = eq,
                                    cor = corSituacao(eq.status, paleta),
                                    onEditar = { viewModel.limparErroSalvar(); editandoId = eq.id },
                                    onEtiqueta = { etiquetaId = eq.id }
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
private fun MensagemEquipamentos(titulo: String, texto: String, botao: String?, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (botao != null) Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(botao) }
        }
    }
}

@Composable
private fun CartaoEquipamento(eq: EquipamentoRow, cor: Color, onEditar: () -> Unit, onEtiqueta: () -> Unit) {
    EmprestaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${eq.marca} ${eq.modelo}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Etiqueta ${eq.etiqueta} · ${eq.tipo}" + if (eq.usoTecnico) " · Uso técnico" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EmprestaStatusBadge(text = eq.status, containerColor = cor.copy(alpha = .16f), contentColor = cor)
            }
            if (!eq.observacao.isNullOrBlank()) {
                Text(eq.observacao, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEditar, modifier = Modifier.semantics { contentDescription = "Editar ${eq.marca} ${eq.modelo}, etiqueta ${eq.etiqueta}" }) { Text("Editar") }
                TextButton(onClick = onEtiqueta, modifier = Modifier.semantics { contentDescription = "Gerar etiqueta de ${eq.marca} ${eq.modelo}, etiqueta ${eq.etiqueta}" }) { Text("Etiqueta") }
            }
        }
    }
}

@Composable
private fun FormularioEquipamentoDialog(
    editando: EquipamentoRow?,
    salvando: Boolean,
    erro: String?,
    onSalvar: (FormularioEquipamento) -> Unit,
    onCancelar: () -> Unit
) {
    val chave = editando?.id ?: -1L
    var marca by rememberSaveable(chave) { mutableStateOf(editando?.marca.orEmpty()) }
    var modelo by rememberSaveable(chave) { mutableStateOf(editando?.modelo.orEmpty()) }
    var etiqueta by rememberSaveable(chave) { mutableStateOf(editando?.etiqueta.orEmpty()) }
    var tipo by rememberSaveable(chave) { mutableStateOf(editando?.tipo.orEmpty()) }
    var usoTecnico by rememberSaveable(chave) { mutableStateOf(editando?.usoTecnico ?: false) }
    var status by rememberSaveable(chave) { mutableStateOf(editando?.status ?: "Disponível") }
    var observacao by rememberSaveable(chave) { mutableStateOf(editando?.observacao.orEmpty()) }

    // Só "Disponível" e "Manutenção" podem ser trocados à mão. "Em uso" muda pelo empréstimo/devolução.
    val statusEditavel = editando != null && normalizarTexto(editando.status) in setOf("disponivel", "manutencao")
    val valido = marca.isNotBlank() && modelo.isNotBlank() && tipo.isNotBlank() && etiqueta.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!salvando) onCancelar() },
        title = { Text(if (editando == null) "Novo equipamento" else "Editar equipamento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(marca, { marca = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Marca") }, singleLine = true, enabled = !salvando)
                OutlinedTextField(modelo, { modelo = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Modelo") }, singleLine = true, enabled = !salvando)
                OutlinedTextField(
                    value = etiqueta,
                    onValueChange = { etiqueta = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Etiqueta (código)") },
                    supportingText = { Text(if (editando == null) "Exemplo: NB-001. Não poderá ser alterada depois." else "A etiqueta não pode ser alterada.") },
                    singleLine = true,
                    enabled = editando == null && !salvando,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
                OutlinedTextField(tipo, { tipo = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tipo") }, singleLine = true, enabled = !salvando)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Notebook", "Tablet").forEach { sugestao ->
                        FilterChip(selected = tipo.trim().equals(sugestao, ignoreCase = true), onClick = { tipo = sugestao }, label = { Text(sugestao) }, enabled = !salvando)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = usoTecnico, enabled = !salvando, role = Role.Switch, onValueChange = { usoTecnico = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Uso técnico", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = usoTecnico, onCheckedChange = null)
                }
                if (editando != null) {
                    Text("Situação", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (statusEditavel) {
                        listOf("Disponível", "Manutenção").forEach { opcao ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .selectable(selected = status == opcao, enabled = !salvando, role = Role.RadioButton, onClick = { status = opcao }),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(selected = status == opcao, onClick = null)
                                Text(opcao, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else {
                        Text(
                            "Situação atual: ${editando.status}. Ela muda automaticamente quando o equipamento é emprestado ou devolvido.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(observacao, { observacao = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Observação (opcional)") }, minLines = 2, enabled = !salvando)
                if (erro != null) {
                    Text(erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSalvar(FormularioEquipamento(marca, modelo, etiqueta, tipo, usoTecnico, status, observacao)) },
                enabled = valido && !salvando
            ) {
                if (salvando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar, enabled = !salvando) { Text("Cancelar") } }
    )
}