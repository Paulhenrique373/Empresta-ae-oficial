package com.example.emprestaa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emprestaa.data.FatiaGrafico
import com.example.emprestaa.data.GestaoUiState
import com.example.emprestaa.data.GestaoViewModel
import com.example.emprestaa.data.ResumoGestao
import com.example.emprestaa.ui.theme.EmprestaCard
import kotlinx.coroutines.delay

/** Atualização automática enquanto o painel está aberto (decisão aprovada: 30 segundos). */
private const val INTERVALO_AUTOMATICO_MS = 30_000L

/**
 * Painel principal da Gestão Escolar. O acesso é validado no servidor (função usuario_e_gestao) antes de
 * qualquer dado ser carregado, e os dados continuam protegidos pela RLS do Supabase.
 *
 * @param usuarioId id do usuário logado; separa o estado do painel entre contas diferentes no mesmo aparelho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestaoDashboard(
    nome: String,
    usuarioId: String,
    onSair: () -> Unit,
    onIr: (String) -> Unit,
    viewModel: GestaoViewModel = viewModel(key = "gestao-$usuarioId")
) {
    val estado by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Carrega ao abrir/voltar ao app e repete a cada 30 s somente enquanto o painel está visível.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.carregar()
            while (true) {
                delay(INTERVALO_AUTOMATICO_MS)
                viewModel.carregar(silencioso = true)
            }
        }
    }

    var menuAberto by remember { mutableStateOf(false) }
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }
    val ocupado = estado.carregando || estado.atualizando

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Sair da conta?") },
            text = { Text("Tem certeza de que deseja sair da sua conta?") },
            confirmButton = {
                TextButton(onClick = { confirmarSaida = false; onSair() }) {
                    Text("Sair", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarSaida = false }, colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())) { Text("Cancelar") } }
        )
    }

    Scaffold(
        bottomBar = { BarraGestao(selecionada = "painel", onIr = onIr) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Painel da Gestão",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            if (nome.isBlank()) "Olá!" else "Olá, ${nome.substringBefore(' ')}!",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.carregar() }, enabled = !estado.acessoNegado) {
                        if (ocupado) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp).semantics { contentDescription = "Atualizando informações" },
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar informações")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuAberto = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                        }
                        DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                            DropdownMenuItem(
                                text = { Text("Sair da conta") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                                onClick = { menuAberto = false; confirmarSaida = true }
                            )
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
                ConteudoGestao(
                    estado = estado,
                    onTentarNovamente = { viewModel.carregar() },
                    onSair = { confirmarSaida = true }
                )
            }
        }
    }
}

@Composable
private fun ConteudoGestao(estado: GestaoUiState, onTentarNovamente: () -> Unit, onSair: () -> Unit) {
    val resumo = estado.resumo
    when {
        estado.acessoNegado -> MensagemCentral(
            titulo = "Acesso restrito",
            texto = "Esta conta não tem permissão de Gestão. Entre com uma conta da Gestão escolar.",
            botao = "Sair da conta",
            onClick = onSair
        )
        resumo == null && estado.erro == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(
                    "Carregando o painel…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        resumo == null -> MensagemCentral(
            titulo = "Não foi possível carregar o painel",
            texto = estado.erro ?: "Tente novamente.",
            botao = "Tentar novamente",
            onClick = onTentarNovamente
        )
        resumo.vazio -> MensagemCentral(
            titulo = "Nenhum dado ainda",
            texto = "Ainda não há equipamentos, empréstimos ou defeitos cadastrados.",
            botao = "Atualizar",
            onClick = onTentarNovamente
        )
        else -> Painel(resumo, estado.atualizadoEm, estado.erro, onTentarNovamente)
    }
}

@Composable
private fun MensagemCentral(titulo: String, texto: String, botao: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(botao) }
        }
    }
}

private class Indicador(val rotulo: String, val valor: String, val destaque: Color? = null)

@Composable
private fun Painel(resumo: ResumoGestao, atualizadoEm: String?, erro: String?, onAtualizar: () -> Unit) {
    val paleta = rememberPaleta()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StatusAtualizacao(atualizadoEm, erro, onAtualizar) }

        item { TituloSecao("Equipamentos") }
        item { LinhaIndicadores(Indicador("Total de equipamentos", resumo.totalEquipamentos.toString()), Indicador("Notebooks", resumo.notebooks.toString())) }
        item { LinhaIndicadores(Indicador("Tablets", resumo.tablets.toString()), Indicador("Disponíveis", resumo.disponiveis.toString(), paleta.verde)) }
        item { LinhaIndicadores(Indicador("Emprestados", resumo.emprestados.toString(), paleta.azul), Indicador("Em manutenção", resumo.manutencao.toString(), paleta.laranja)) }

        item { TituloSecao("Pessoas e empréstimos") }
        item { LinhaIndicadores(Indicador("Empréstimos ativos", resumo.emprestimosAtivos.toString()), Indicador("Professores cadastrados", resumo.professores?.toString() ?: "–")) }

        item { TituloSecao("Defeitos") }
        item { LinhaIndicadores(Indicador("Defeitos registrados", resumo.defeitosRegistrados.toString()), Indicador("Defeitos em aberto", resumo.defeitosEmAberto.toString(), if (resumo.defeitosEmAberto > 0) paleta.laranja else null)) }

        item { TituloSecao("Gráficos") }
        item {
            CartaoGrafico("Situação dos equipamentos") {
                GraficoRosca(resumo.situacaoEquipamentos, { corSituacao(it, paleta) }, resumo.totalEquipamentos)
            }
        }
        item {
            CartaoGrafico("Equipamentos por tipo") {
                GraficoBarras(resumo.equipamentosPorTipo, paleta, "Nenhum equipamento cadastrado.")
            }
        }
        item {
            CartaoGrafico("Defeitos por situação") {
                GraficoBarras(resumo.defeitosPorSituacao, paleta, "Nenhum defeito registrado.")
            }
        }
    }
}

@Composable
private fun StatusAtualizacao(atualizadoEm: String?, erro: String?, onAtualizar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            buildString {
                append(if (atualizadoEm != null) "Atualizado às $atualizadoEm. " else "")
                append("Atualiza sozinho a cada 30 segundos enquanto este painel está aberto.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (erro != null) {
            Row(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Não foi possível atualizar. $erro",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onAtualizar, colors = ButtonDefaults.textButtonColors(contentColor = corBotaoTexto())) { Text("Tentar novamente") }
            }
        }
    }
}

@Composable
private fun TituloSecao(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() }
    )
}

@Composable
private fun LinhaIndicadores(a: Indicador, b: Indicador) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CartaoIndicador(a, Modifier.weight(1f).fillMaxHeight())
        CartaoIndicador(b, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun CartaoIndicador(indicador: Indicador, modifier: Modifier) {
    EmprestaCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp).clearAndSetSemantics { contentDescription = "${indicador.rotulo}: ${indicador.valor}" },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                indicador.valor,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = indicador.destaque ?: MaterialTheme.colorScheme.onSurface
            )
            Text(indicador.rotulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CartaoGrafico(titulo: String, conteudo: @Composable () -> Unit) {
    EmprestaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            conteudo()
        }
    }
}

/** Cores dos gráficos: legíveis no tema claro e no escuro. Sempre acompanhadas de texto e número. */
internal class Paleta(val verde: Color, val azul: Color, val laranja: Color, val cinza: Color, val serie: List<Color>)

/** Cor legível para botões de texto: no tema escuro a cor primária (azul-marinho) some no fundo, então usa um azul claro. */
@Composable
internal fun corBotaoTexto(): Color {
    val cores = MaterialTheme.colorScheme
    return if (cores.background.luminance() < 0.5f) cores.tertiary else cores.primary
}

@Composable
internal fun rememberPaleta(): Paleta {
    val escuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(escuro) {
        if (escuro) Paleta(
            verde = Color(0xFF4CC38A), azul = Color(0xFF7FB2F0), laranja = Color(0xFFFFC247), cinza = Color(0xFFB7C7D8),
            serie = listOf(Color(0xFF7FB2F0), Color(0xFF4CC38A), Color(0xFFFFC247), Color(0xFFD7A6F5), Color(0xFFB7C7D8))
        ) else Paleta(
            verde = Color(0xFF287653), azul = Color(0xFF2F6DB5), laranja = Color(0xFF956500), cinza = Color(0xFF60758D),
            serie = listOf(Color(0xFF2F6DB5), Color(0xFF287653), Color(0xFF956500), Color(0xFF7A4BB5), Color(0xFF60758D))
        )
    }
}

private fun corSituacao(fatia: FatiaGrafico, paleta: Paleta): Color = when (fatia.chave) {
    "disponivel" -> paleta.verde
    "em uso" -> paleta.azul
    "manutencao" -> paleta.laranja
    else -> paleta.cinza
}

@Composable
private fun GraficoRosca(fatias: List<FatiaGrafico>, cor: (FatiaGrafico) -> Color, total: Int) {
    if (total == 0) {
        Text("Nenhum equipamento cadastrado.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val trilho = MaterialTheme.colorScheme.outlineVariant
    val descricao = "Situação dos equipamentos. " + fatias.joinToString(". ") { "${it.rotulo}: ${it.valor}" }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(160.dp).clearAndSetSemantics { contentDescription = descricao }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val traco = 26.dp.toPx()
                val lado = size.minDimension - traco
                val canto = Offset((size.width - lado) / 2f, (size.height - lado) / 2f)
                drawArc(color = trilho, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = canto, size = Size(lado, lado), style = Stroke(traco))
                var inicio = -90f
                fatias.forEach { fatia ->
                    if (fatia.valor > 0) {
                        val angulo = 360f * fatia.valor / total
                        drawArc(color = cor(fatia), startAngle = inicio, sweepAngle = angulo, useCenter = false, topLeft = canto, size = Size(lado, lado), style = Stroke(traco))
                        inicio += angulo
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("equipamentos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fatias.forEach { fatia ->
                val porcentagem = Math.round(100f * fatia.valor / total)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(14.dp).background(cor(fatia), CircleShape))
                    Text("${fatia.rotulo}: ${fatia.valor} ($porcentagem%)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun GraficoBarras(fatias: List<FatiaGrafico>, paleta: Paleta, textoVazio: String) {
    if (fatias.isEmpty()) {
        Text(textoVazio, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val maximo = fatias.maxOf { it.valor }.coerceAtLeast(1)
    val trilho = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fatias.forEachIndexed { indice, fatia ->
            val cor = paleta.serie[indice % paleta.serie.size]
            Column(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${fatia.rotulo}: ${fatia.valor}" },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fatia.rotulo, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(fatia.valor.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val raio = CornerRadius(7.dp.toPx())
                    drawRoundRect(color = trilho, size = size, cornerRadius = raio)
                    if (fatia.valor > 0) {
                        val largura = (size.width * fatia.valor / maximo).coerceAtLeast(14.dp.toPx())
                        drawRoundRect(color = cor, size = Size(largura, size.height), cornerRadius = raio)
                    }
                }
            }
        }
    }
}

/** Barra inferior da área da Gestão. Cada etapa nova acrescenta um destino aqui. */
@Composable
internal fun BarraGestao(selecionada: String, onIr: (String) -> Unit) {
    NavigationBar {
        listOf(
            Triple("painel", "Painel", Icons.Default.Home),
            Triple("equipamentos", "Equipamentos", Icons.Default.Devices),
            Triple("emprestimos", "Empréstimos", Icons.Default.SwapHoriz)
        ).forEach { (destino, rotulo, icone) ->
            NavigationBarItem(
                selected = selecionada == destino,
                onClick = { if (selecionada != destino) onIr(destino) },
                icon = { Icon(icone, contentDescription = null) },
                label = { Text(rotulo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/** Raiz da área da Gestão: alterna entre o painel e as telas de gerenciamento. */
@Composable
fun GestaoApp(nome: String, usuarioId: String, onSair: () -> Unit) {
    var tela by rememberSaveable { mutableStateOf("painel") }
    // O botão Voltar do celular leva de volta ao painel.
    BackHandler(enabled = tela != "painel") { tela = "painel" }
    if (tela == "equipamentos") {
        GestaoEquipamentos(usuarioId = usuarioId, onIr = { tela = it })
    } else if (tela == "emprestimos") {
        GestaoEmprestimos(usuarioId = usuarioId, onIr = { tela = it })
    } else {
        GestaoDashboard(nome = nome, usuarioId = usuarioId, onSair = onSair, onIr = { tela = it })
    }
}