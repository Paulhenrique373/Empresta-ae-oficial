package com.example.emprestaa

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import com.example.emprestaa.data.CategoriaAuditoria
import com.example.emprestaa.data.GestaoAuditoriaViewModel
import com.example.emprestaa.data.RegistroAuditoria
import com.example.emprestaa.data.ResumoAuditoria
import com.example.emprestaa.data.TipoPeriodo
import com.example.emprestaa.data.calcularIntervalo
import com.example.emprestaa.data.filtrarAuditoria
import com.example.emprestaa.ui.theme.EmprestaCard
import com.example.emprestaa.ui.theme.EmprestaIconTile
import com.example.emprestaa.ui.theme.EmprestaStatusBadge
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Histórico e auditoria da Gestão. SOMENTE CONSULTA: a tela não tem nenhum botão ou ação para editar,
 * apagar ou criar registros de auditoria. O acesso é validado no servidor (usuario_e_gestao) e os
 * dados continuam protegidos pela RLS do Supabase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestaoAuditoria(
    usuarioId: String,
    onIr: (String) -> Unit,
    viewModel: GestaoAuditoriaViewModel = viewModel(key = "gestao-auditoria-$usuarioId")
) {
    val estado by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lê ao abrir/voltar ao app. Leituras com menos de 60 s são reaproveitadas (sem nova consulta).
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.carregar()
        }
    }

    var consulta by rememberSaveable { mutableStateOf("") }
    var categoriaNome by rememberSaveable { mutableStateOf<String?>(null) }
    var usuarioFiltro by rememberSaveable { mutableStateOf<String?>(null) }
    var periodoNome by rememberSaveable { mutableStateOf(TipoPeriodo.TUDO.name) }
    var inicioDia by rememberSaveable { mutableStateOf<Long?>(null) }
    var fimDia by rememberSaveable { mutableStateOf<Long?>(null) }
    var escolhendoDatas by rememberSaveable { mutableStateOf(false) }
    var detalheChave by rememberSaveable { mutableStateOf<String?>(null) }

    val categoria = CategoriaAuditoria.entries.firstOrNull { it.name == categoriaNome }
    val tipoPeriodo = TipoPeriodo.entries.firstOrNull { it.name == periodoNome } ?: TipoPeriodo.TUDO
    val registros = estado.registros

    val intervalo = remember(tipoPeriodo, inicioDia, fimDia, estado.atualizadoEm) {
        val zona = ZoneId.systemDefault()
        calcularIntervalo(
            tipo = tipoPeriodo,
            hoje = LocalDate.now(zona),
            zona = zona,
            inicioPersonalizado = inicioDia?.let { LocalDate.ofEpochDay(it) },
            fimPersonalizado = fimDia?.let { LocalDate.ofEpochDay(it) }
        )
    }

    val filtrados = remember(registros, consulta, categoria, usuarioFiltro, intervalo) {
        if (registros == null) emptyList()
        else filtrarAuditoria(registros, consulta, categoria, usuarioFiltro, intervalo)
    }

    // Categorias e usuários que realmente existem nos registros lidos.
    val categoriasPresentes = remember(registros) {
        val existentes = registros.orEmpty().map { it.categoria }.toSet()
        CategoriaAuditoria.entries.filter { it in existentes }
    }
    val usuariosDisponiveis = remember(registros) {
        registros.orEmpty()
            .filter { it.usuarioId != null }
            .distinctBy { it.usuarioId }
            .map { r ->
                val id = r.usuarioId.orEmpty()
                id to (r.usuario ?: "Usuário não identificado (${id.take(8)})")
            }
            .sortedBy { it.second.lowercase() }
    }

    val filtrosAtivos = consulta.isNotBlank() || categoria != null || usuarioFiltro != null ||
            tipoPeriodo != TipoPeriodo.TUDO

    fun limparFiltros() {
        consulta = ""
        categoriaNome = null
        usuarioFiltro = null
        periodoNome = TipoPeriodo.TUDO.name
        inicioDia = null
        fimDia = null
    }

    if (escolhendoDatas) {
        SeletorPeriodoAuditoriaDialog(
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

    val detalhe = detalheChave?.let { chave -> registros?.firstOrNull { it.chave == chave } }
    if (detalhe != null) {
        DetalheAuditoriaDialog(registro = detalhe, onFechar = { detalheChave = null })
    }

    val ocupado = estado.carregando || estado.atualizando

    Scaffold(
        bottomBar = { BarraGestao(selecionada = "auditoria", onIr = onIr) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onIr("painel") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar ao painel")
                    }
                },
                title = {
                    Text(
                        "Histórico e auditoria",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.carregar(forcar = true) }, enabled = !estado.acessoNegado) {
                        if (ocupado) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp).semantics { contentDescription = "Atualizando o histórico" },
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar o histórico")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxHeight().align(Alignment.TopCenter)) {
                when {
                    estado.acessoNegado -> MensagemAuditoria(
                        titulo = "Acesso restrito à Gestão",
                        texto = "Esta conta não tem permissão de Gestão para consultar o histórico e a auditoria.",
                        botao = null,
                        onClick = {}
                    )

                    estado.carregando && registros == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Carregando histórico…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }

                    estado.erro != null && registros == null -> MensagemAuditoria(
                        titulo = "Não foi possível carregar o histórico",
                        texto = estado.erro ?: "Tente novamente.",
                        botao = "Tentar novamente",
                        onClick = { viewModel.carregar(forcar = true) }
                    )

                    registros != null && registros.isEmpty() -> MensagemAuditoria(
                        titulo = "Nenhum registro de auditoria",
                        texto = "A consulta não retornou registros para esta conta. Se você esperava ver registros, " +
                                "confira no Supabase se a tabela de auditoria tem dados e se a política de leitura (RLS) permite a Gestão ler.",
                        botao = "Atualizar",
                        onClick = { viewModel.carregar(forcar = true) }
                    )

                    registros != null -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Acompanhe quem fez o quê, quando e em qual equipamento. Esta tela é somente de consulta: " +
                                            "os registros originais não podem ser alterados nem apagados por aqui.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                estado.atualizadoEm?.let {
                                    Text(
                                        "Atualizado às $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                    )
                                }
                            }
                        }

                        if (estado.erro != null) {
                            item {
                                AvisoAuditoria(
                                    texto = "Não foi possível atualizar: ${estado.erro}. Os dados abaixo são da última leitura.",
                                    erro = true,
                                    acao = "Tentar novamente",
                                    onAcao = { viewModel.carregar(forcar = true) }
                                )
                            }
                        }
                        if (estado.usuariosIndisponiveis) {
                            item {
                                AvisoAuditoria(
                                    texto = "Não foi possível ler a lista de usuários; nomes de responsáveis podem aparecer como não identificados.",
                                    erro = false
                                )
                            }
                        }
                        if (estado.equipamentosIndisponiveis) {
                            item {
                                AvisoAuditoria(
                                    texto = "Não foi possível ler a lista de equipamentos; etiquetas e nomes podem não aparecer.",
                                    erro = false
                                )
                            }
                        }
                        if (estado.possivelLimite) {
                            item {
                                AvisoAuditoria(
                                    texto = "Foram lidos 1000 registros, que é o limite padrão do servidor. Pode haver registros mais antigos que não aparecem aqui.",
                                    erro = false
                                )
                            }
                        }

                        estado.resumo?.let { resumo ->
                            item { IndicadoresAuditoria(resumo) }
                        }

                        item {
                            OutlinedTextField(
                                value = consulta,
                                onValueChange = { consulta = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                label = { Text("Pesquisar no histórico") },
                                placeholder = { Text("Usuário, ação, equipamento ou descrição") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (consulta.isNotEmpty()) {
                                        IconButton(onClick = { consulta = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Limpar pesquisa")
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RotuloSecaoAuditoria("Categoria")
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = categoria == null,
                                        onClick = { categoriaNome = null },
                                        label = { Text("Todas") }
                                    )
                                    categoriasPresentes.forEach { c ->
                                        FilterChip(
                                            selected = categoria == c,
                                            onClick = { categoriaNome = c.name },
                                            label = { Text(c.rotulo) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RotuloSecaoAuditoria("Período")
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TipoPeriodo.entries.forEach { t ->
                                        FilterChip(
                                            selected = tipoPeriodo == t,
                                            onClick = {
                                                if (t == TipoPeriodo.PERSONALIZADO) escolhendoDatas = true
                                                else periodoNome = t.name
                                            },
                                            label = { Text(t.rotulo) }
                                        )
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Período aplicado: ${intervalo.descricao}",
                                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (tipoPeriodo == TipoPeriodo.PERSONALIZADO) {
                                        TextButton(
                                            onClick = { escolhendoDatas = true },
                                            colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())
                                        ) { Text("Alterar datas") }
                                    }
                                }
                            }
                        }

                        item {
                            var menuAberto by remember { mutableStateOf(false) }
                            val nomeSelecionado = usuarioFiltro?.let { sel ->
                                usuariosDisponiveis.firstOrNull { it.first == sel }?.second
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RotuloSecaoAuditoria("Usuário")
                                Box {
                                    OutlinedButton(
                                        onClick = { menuAberto = true },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            nomeSelecionado ?: "Todos os usuários",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = corBotaoTexto()
                                        )
                                    }
                                    DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Todos os usuários") },
                                            onClick = { usuarioFiltro = null; menuAberto = false }
                                        )
                                        usuariosDisponiveis.forEach { (id, nome) ->
                                            DropdownMenuItem(
                                                text = { Text(nome) },
                                                onClick = { usuarioFiltro = id; menuAberto = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Mostrando ${filtrados.size} de ${registros.size} registros",
                                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (filtrosAtivos) {
                                    TextButton(
                                        onClick = { limparFiltros() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())
                                    ) { Text("Limpar filtros") }
                                }
                            }
                        }

                        if (filtrados.isEmpty()) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Nenhum registro encontrado",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Nenhum registro combina com a pesquisa e os filtros escolhidos.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(filtrados, key = { it.chave }) { registro ->
                                CartaoRegistroAuditoria(registro) { detalheChave = registro.chave }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun estiloCategoria(categoria: CategoriaAuditoria): Triple<ImageVector, Color, Color> {
    val cores = MaterialTheme.colorScheme
    return when (categoria) {
        CategoriaAuditoria.EMPRESTIMO -> Triple(Icons.Default.SwapHoriz, cores.primaryContainer, cores.onPrimaryContainer)
        CategoriaAuditoria.DEVOLUCAO -> Triple(Icons.AutoMirrored.Filled.Undo, cores.secondaryContainer, cores.onSecondaryContainer)
        CategoriaAuditoria.EQUIPAMENTO -> Triple(Icons.Default.Devices, cores.tertiaryContainer, cores.onTertiaryContainer)
        CategoriaAuditoria.OCORRENCIA -> Triple(Icons.Default.Warning, cores.errorContainer, cores.onErrorContainer)
        CategoriaAuditoria.ADMINISTRATIVA -> Triple(Icons.Default.Info, cores.surfaceVariant, cores.onSurfaceVariant)
        CategoriaAuditoria.OUTRAS -> Triple(Icons.Default.History, cores.surfaceVariant, cores.onSurfaceVariant)
    }
}

@Composable
private fun RotuloSecaoAuditoria(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun MensagemAuditoria(titulo: String, texto: String, botao: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }
        )
        Spacer(Modifier.height(8.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (botao != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(botao, color = corBotaoTexto())
            }
        }
    }
}

@Composable
private fun AvisoAuditoria(texto: String, erro: Boolean, acao: String? = null, onAcao: () -> Unit = {}) {
    val cores = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (erro) cores.errorContainer else cores.secondaryContainer,
        contentColor = if (erro) cores.onErrorContainer else cores.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(texto, style = MaterialTheme.typography.bodySmall)
            if (acao != null) {
                TextButton(onClick = onAcao, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(acao, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun IndicadoresAuditoria(resumo: ResumoAuditoria) {
    val itens = buildList {
        add(Triple("Total de registros", resumo.total, Icons.Default.History))
        add(Triple("Últimos 7 dias", resumo.recentes, Icons.Default.Refresh))
        add(Triple("Empréstimos", resumo.emprestimos, Icons.Default.SwapHoriz))
        add(Triple("Devoluções", resumo.devolucoes, Icons.AutoMirrored.Filled.Undo))
        add(Triple("Equipamentos", resumo.equipamentos, Icons.Default.Devices))
        add(Triple("Ocorrências", resumo.ocorrencias, Icons.Default.Warning))
        if (resumo.administrativas > 0) add(Triple("Alterações administrativas", resumo.administrativas, Icons.Default.Info))
        if (resumo.outras > 0) add(Triple("Outras atividades", resumo.outras, Icons.Default.Info))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itens.chunked(2).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { (rotulo, valor, icone) ->
                    CartaoIndicadorAuditoria(rotulo, valor, icone, Modifier.weight(1f))
                }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CartaoIndicadorAuditoria(rotulo: String, valor: Int, icone: ImageVector, modifier: Modifier) {
    EmprestaCard(modifier.clearAndSetSemantics { contentDescription = "$rotulo: $valor" }) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(valor.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(rotulo, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CartaoRegistroAuditoria(registro: RegistroAuditoria, onAbrir: () -> Unit) {
    val (icone, fundo, conteudo) = estiloCategoria(registro.categoria)
    val titulo = registro.titulo
    val responsavel = registro.usuario ?: "Usuário não identificado"
    EmprestaCard(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Ver detalhes do registro", role = Role.Button, onClick = onAbrir)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmprestaIconTile(icon = icone, contentDescription = null, tint = conteudo, containerColor = fundo)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Responsável: $responsavel", style = MaterialTheme.typography.bodySmall)
                registro.descricao?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                registro.equipamento?.let { Text("Equipamento: $it", style = MaterialTheme.typography.bodySmall) }
                registro.emprestimoId?.let { Text("Empréstimo nº $it", style = MaterialTheme.typography.bodySmall) }
                registro.ocorrenciaId?.let { Text("Ocorrência nº $it", style = MaterialTheme.typography.bodySmall) }
                Text(
                    registro.dataTexto ?: "Data não registrada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            EmprestaStatusBadge(registro.categoria.rotulo, containerColor = fundo, contentColor = conteudo)
        }
    }
}

@Composable
private fun CampoDetalhe(rotulo: String, valor: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetalheAuditoriaDialog(registro: RegistroAuditoria, onFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Detalhes do registro", modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CampoDetalhe("Registro", registro.id?.let { "Nº $it" } ?: "Sem identificação no banco")
                CampoDetalhe(
                    "Usuário responsável",
                    registro.usuario
                        ?: registro.usuarioId?.let { "Não encontrado na lista de usuários (id $it)" }
                        ?: "Não informado no registro"
                )
                CampoDetalhe("Ação realizada", registro.titulo)
                CampoDetalhe("Operação no banco", registro.acao ?: "Não informada")
                CampoDetalhe("Categoria", registro.categoria.rotulo)
                registro.descricao?.let { CampoDetalhe("Descrição", it) }
                CampoDetalhe("Data e horário", registro.dataTexto ?: "Não registrados")

                val entidade = listOfNotNull(
                    registro.tabela,
                    registro.registroId?.let { "registro $it" }
                ).joinToString(" · ")
                if (entidade.isNotEmpty()) CampoDetalhe("Entidade relacionada", entidade)

                registro.equipamento?.let { CampoDetalhe("Equipamento", it) }
                registro.equipamentoId?.let { CampoDetalhe("Id do equipamento", it.toString()) }
                registro.emprestimoId?.let { CampoDetalhe("Empréstimo", "Nº $it") }
                registro.ocorrenciaId?.let { CampoDetalhe("Ocorrência", "Nº $it") }

                if (registro.anterior != null || registro.posterior != null) {
                    registro.anterior?.let { CampoDetalhe("Situação anterior", it) }
                    registro.posterior?.let { CampoDetalhe("Situação posterior", it) }
                } else {
                    Text(
                        "Este registro não armazena situação anterior nem posterior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (registro.adicionais.isNotEmpty()) {
                    HorizontalDivider()
                    RotuloSecaoAuditoria(if (registro.anterior != null || registro.posterior != null) "O que mudou / dados do registro" else "Informações adicionais")
                    registro.adicionais.forEach { (rotulo, valor) -> CampoDetalhe(rotulo, valor) }
                }

                if (registro.campos.isNotEmpty()) {
                    HorizontalDivider()
                    RotuloSecaoAuditoria("Dados originais do banco")
                    registro.campos.forEach { (rotulo, valor) -> CampoDetalhe(rotulo, valor) }
                }

                Text(
                    "Somente consulta: registros de auditoria não podem ser editados nem apagados pelo aplicativo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onFechar,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())
            ) { Text("Fechar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorPeriodoAuditoriaDialog(
    inicio: LocalDate?,
    fim: LocalDate?,
    onConfirmar: (LocalDate, LocalDate) -> Unit,
    onCancelar: () -> Unit
) {
    // O seletor do Material 3 trabalha em UTC; a conversão para o fuso do aparelho é feita em calcularIntervalo.
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
                    if (msInicio != null) onConfirmar(paraData(msInicio), paraData(msFim ?: msInicio))
                },
                colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())
            ) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())) {
                Text("Cancelar")
            }
        }
    ) {
        DateRangePicker(state = seletor, modifier = Modifier.height(500.dp))
    }
}