package com.example.emprestaa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.selection.toggleable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import com.example.emprestaa.ui.theme.EmprestaAêTheme
import com.example.emprestaa.ui.theme.AvailableGreen
import com.example.emprestaa.ui.theme.PendingOrange
import com.example.emprestaa.data.AuthViewModel
import com.example.emprestaa.data.PerfilAcesso
import com.example.emprestaa.data.AuthState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emprestaa.data.EquipmentViewModel
import com.example.emprestaa.data.DefectViewModel
import com.example.emprestaa.data.DefeitoInsert
import com.example.emprestaa.data.LoanViewModel
import com.example.emprestaa.data.SupabaseProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class Equipamento(val id: String, val codigo: String, val nome: String, val tipo: String, val status: String = "Disponível")
@Serializable
data class Emprestimo(val id: String, val professor: String, val sala: String, val codigos: List<String>, val inicio: String, val devolucao: String? = null, val devolvidoPor: String? = null, val devolvidos: List<String> = emptyList())
@Serializable
data class Problema(val id: String, val codigo: String, val descricao: String, val data: String)
@Serializable
data class BancoLocal(val equipamentos: List<Equipamento>, val emprestimos: List<Emprestimo>, val problemas: List<Problema>)

private fun formatarData(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}.getOrElse { iso.replace('T', ' ').take(16) }

private fun agora(): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())

// true quando "Contraste alto" está ligado; usado onde o app aplica transparência fixa em cores.
private val LocalAltoContraste = compositionLocalOf { false }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SupabaseProvider.init(applicationContext)
        setContent { EmprestaAppRoot() }
    }
}

@Composable
private fun EmprestaAppRoot(authViewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("empresta-ae-local", 0) }
    var modo by remember { mutableStateOf(prefs.getString("modo_visual", "Aparelho") ?: "Aparelho") }
    var acessibilidade by remember { mutableStateOf(AcessibilidadeStorage.carregar(prefs)) }
    var nomeProfessor by remember { mutableStateOf(prefs.getString("nome_professor", "") ?: "") }
    var demonstracaoAtiva by remember { mutableStateOf(prefs.getString("supabase_user_id", null) != null) }
    // Quem tem o perfil Gestão (mesmo com outros perfis) abre o painel da Gestão. O painel confirma isso no servidor.
    var areaGestao by remember { mutableStateOf(prefs.getBoolean("perfil_gestao", false)) }
    val dark = modo == "Escuro" || (modo == "Aparelho" && isSystemInDarkTheme())
    // Se o Supabase perder a sessão (ex.: token recusado), volta ao login em vez de mostrar dados vazios.
    val sessaoPerdida by authViewModel.sessaoPerdida.collectAsState()
    LaunchedEffect(sessaoPerdida, demonstracaoAtiva) {
        if (sessaoPerdida && demonstracaoAtiva) {
            prefs.edit().remove("supabase_user_id").remove("nome_professor").apply()
            nomeProfessor = ""
            demonstracaoAtiva = false
        }
    }
    // Tamanho do texto global: multiplica o tamanho de fonte do sistema (afeta todos os sp do app).
    val densidadeSistema = LocalDensity.current
    val escalaFonte = (densidadeSistema.fontScale * acessibilidade.tamanhoTexto.fator).coerceAtMost(maxOf(densidadeSistema.fontScale, 2.0f))
    val densidadeApp = remember(densidadeSistema.density, escalaFonte) { Density(densidadeSistema.density, escalaFonte) }
    val sairDaConta: () -> Unit = {
        authViewModel.logout()
        prefs.edit()
            .remove("supabase_user_id")
            .remove("nome_professor")
            .remove("perfil_gestao")
            .apply()
        nomeProfessor = ""
        areaGestao = false
        demonstracaoAtiva = false
    }
    EmprestaAêTheme(darkTheme = dark, altoContraste = acessibilidade.altoContraste) {
        CompositionLocalProvider(
            LocalDensity provides densidadeApp,
            LocalAltoContraste provides acessibilidade.altoContraste,
            LocalMinimumInteractiveComponentSize provides (if (acessibilidade.toqueAmpliado) 56.dp else 48.dp)
        ) {
            if (demonstracaoAtiva && areaGestao) {
                GestaoApp(
                    nome = nomeProfessor,
                    usuarioId = prefs.getString("supabase_user_id", "") ?: "",
                    onSair = sairDaConta
                )
            } else if (demonstracaoAtiva) {
                EmprestaApp(
                    modoVisual = modo,
                    nomeProfessor = nomeProfessor,
                    onModoVisual = { modo = it; prefs.edit().putString("modo_visual", it).apply() },
                    acessibilidade = acessibilidade,
                    onAcessibilidade = { novo -> acessibilidade = novo; AcessibilidadeStorage.salvar(prefs, novo) },
                    onSair = sairDaConta
                )
            } else TelaLogin(authViewModel, onContinuarDemonstracao = { user ->
                val ehGestao = PerfilAcesso.GESTAO in user.perfis
                prefs.edit()
                    .putString("supabase_user_id", user.id)
                    .putString("nome_professor", user.nome)
                    .putBoolean("perfil_gestao", ehGestao)
                    .apply()
                nomeProfessor = user.nome; areaGestao = ehGestao; demonstracaoAtiva = true
            })
        }
    }
}

@Composable
private fun TelaLogin(authViewModel: AuthViewModel, onContinuarDemonstracao: (com.example.emprestaa.data.SessaoUsuario) -> Unit) {
    var usuario by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erroUsuario by remember { mutableStateOf(false) }
    var erroSenha by remember { mutableStateOf(false) }
    var mostrarSenha by remember { mutableStateOf(false) }
    var mensagemLogin by remember { mutableStateOf<String?>(null) }
    var mostrarRecuperacao by remember { mutableStateOf(false) }
    var mostrarAvisoDemo by remember { mutableStateOf(false) }
    val senhaFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val contexto = LocalContext.current
    val authState by authViewModel.state.collectAsState()
    var recuperacaoEnviada by remember { mutableStateOf(false) }
    // Um só caminho para o botão "Entrar" e para o "OK" do teclado.
    fun tentarEntrar() {
        erroUsuario = usuario.isBlank(); erroSenha = senha.isBlank(); mensagemLogin = null
        if (erroUsuario || erroSenha) return
        if (!usuario.contains("@")) { mensagemLogin = "Digite o e-mail completo, com @ e domínio."; return }
        focusManager.clearFocus()
        authViewModel.login(usuario, senha)
    }
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.SignedIn -> onContinuarDemonstracao(state.user)
            is AuthState.Error -> {
                if (mostrarRecuperacao && state.message.startsWith("Se o e-mail")) recuperacaoEnviada = true
                else mensagemLogin = state.message
            }
            else -> Unit
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.Devices, contentDescription = "Símbolo Empresta Aê", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(15.dp).size(38.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Empresta Aê", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Controle de equipamentos escolares", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Acesse sua conta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Informe os dados fornecidos pela sua escola.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = usuario, onValueChange = { usuario = it; erroUsuario = false; mensagemLogin = null },
                            modifier = Modifier.fillMaxWidth(), label = { Text("E-mail") }, placeholder = { Text("nome@escola.com") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }, singleLine = true,
                            isError = erroUsuario, supportingText = { if (erroUsuario) Text("Informe seu e-mail.") },
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { senhaFocus.requestFocus() })
                        )
                        OutlinedTextField(
                            value = senha, onValueChange = { senha = it; erroSenha = false; mensagemLogin = null },
                            modifier = Modifier.fillMaxWidth().focusRequester(senhaFocus), label = { Text("Senha") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, singleLine = true,
                            visualTransformation = if (mostrarSenha) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { IconButton(onClick = { mostrarSenha = !mostrarSenha }, modifier = Modifier.size(48.dp)) { Icon(if (mostrarSenha) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (mostrarSenha) "Ocultar senha" else "Mostrar senha") } },
                            isError = erroSenha, supportingText = { if (erroSenha) Text("Informe sua senha.") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                            keyboardActions = KeyboardActions(onDone = { tentarEntrar() })
                        )
                        TextButton(onClick = { mostrarRecuperacao = true }, modifier = Modifier.align(Alignment.End)) { Text("Esqueci minha senha") }
                        mensagemLogin?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = { tentarEntrar() }, enabled = authState !is AuthState.Loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { if (authState is AuthState.Loading) CircularProgressIndicator(Modifier.size(22.dp)) else Text("Entrar") }
                    }
                }
                Text("Ainda não tem acesso? Solicite suas credenciais à administração da escola.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Text("Empresta Aê · Controle de equipamentos escolares\nVersão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    if (mostrarRecuperacao) AlertDialog(onDismissRequest = { mostrarRecuperacao = false }, title = { Text("Recuperar acesso") }, text = { Text("A recuperação de senha ainda não está configurada. Entre em contato com a administração da sua escola para receber ajuda.") }, confirmButton = { TextButton(onClick = { mostrarRecuperacao = false }) { Text("Entendi") } })
}

/** Resultado da atualização manual, mostrado só na tela de Configurações. */
private data class ResultadoAtualizacao(val sucesso: Boolean, val texto: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmprestaApp(modoVisual: String, nomeProfessor: String, onModoVisual: (String) -> Unit, onSair: () -> Unit, acessibilidade: PreferenciasAcessibilidade, onAcessibilidade: (PreferenciasAcessibilidade) -> Unit, equipmentViewModel: EquipmentViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("empresta-ae-local", 0) }
    var banco by remember { mutableStateOf(BancoLocal(emptyList(), emptyList(), emptyList())) }
    val equipamentosState by equipmentViewModel.state.collectAsState()
    val userId = prefs.getString("supabase_user_id", null).orEmpty()
    val defectViewModel: DefectViewModel = viewModel(key = "defeitos-$userId")
    val defectState by defectViewModel.state.collectAsState()
    val loanViewModel: LoanViewModel = viewModel(key = "emprestimos-$userId")
    val loanState by loanViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Atualiza sozinho enquanto o app está aberto: quando outro professor pega ou devolve um equipamento, a tela acompanha.
    LaunchedEffect(userId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var primeira = true
            var tentativas = 0
            while (true) {
                val trabalhos = buildList<kotlinx.coroutines.Job> {
                    add(equipmentViewModel.carregar(silencioso = !primeira))
                    add(defectViewModel.carregar(silencioso = !primeira))
                    if (userId.isNotBlank()) add(loanViewModel.carregar(userId, silencioso = !primeira))
                }
                trabalhos.joinAll()
                // Se a carga "de verdade" falhou (ex.: internet ainda voltando ao reabrir o app), tenta de novo logo, até 3 vezes.
                val falhou = primeira && (equipmentViewModel.state.value.error != null || (userId.isNotBlank() && loanViewModel.state.value.error != null))
                if (falhou && tentativas < 3) { tentativas++; delay(2_000) }
                else { primeira = false; tentativas = 0; delay(10_000) }
            }
        }
    }
    LaunchedEffect(loanState.rows, loanState.loading, loanState.error, equipamentosState.items) {
        if (!loanState.loading && loanState.error == null && userId.isNotBlank()) {
            val mapped = loanState.rows.sortedByDescending { it.retirada }.map { row ->
                // O app identifica equipamento pela etiqueta; o banco guarda o id.
                val etiqueta = equipamentosState.items.firstOrNull { it.id == row.equipamentoId }?.etiqueta ?: row.equipamentoId.toString()
                Emprestimo(row.id.toString(), row.usuarioId, "", listOf(etiqueta), formatarData(row.retirada), row.devolucao?.let { formatarData(it) }, devolvidos = if (row.status == "Devolvido") listOf(etiqueta) else emptyList())
            }
            banco = banco.copy(emprestimos = mapped)
        }
    }
    LaunchedEffect(equipamentosState) {
        val remote = equipamentosState.items
        if (!equipamentosState.loading && equipamentosState.error == null) banco = banco.copy(equipamentos = remote.map { Equipamento(it.id.toString(), it.etiqueta, "${it.marca} ${it.modelo}", it.tipo, it.status) })
    }
    LaunchedEffect(defectState.rows, equipamentosState.items) {
        if (defectState.error == null && !defectState.loading) banco = banco.copy(problemas = defectState.rows.filter { it.usuarioId == userId }.sortedByDescending { it.data }.map { d ->
            Problema(d.id.toString(), equipamentosState.items.firstOrNull { it.id == d.equipamentoId }?.etiqueta ?: d.equipamentoId.toString(), "${d.tipo}: ${d.descricao} (${d.status})", formatarData(d.data))
        })
    }

    var aba by rememberSaveable { mutableStateOf("Início") }
    // Configurações abre pela engrenagem (não é uma aba); guarda de onde veio para o botão Voltar.
    var abaAnterior by rememberSaveable { mutableStateOf("Início") }
    var menuAberto by remember { mutableStateOf(false) }
    var mostrarAjuda by remember { mutableStateOf(false) }
    var mostrarSuporte by remember { mutableStateOf(false) }
    var pesquisa by remember { mutableStateOf("") }
    var filtroEquipamento by remember { mutableStateOf("Todos") }
    var filtroAtividade by remember { mutableStateOf("Todos") }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var mostrarProblema by remember { mutableStateOf(false) }
    var scannerVisible by remember { mutableStateOf(false) }
    var codigoEscaneado by remember { mutableStateOf<String?>(null) }
    var equipamentoEtiqueta by remember { mutableStateOf<Equipamento?>(null) }
    var problemaCodigo by remember { mutableStateOf("") }
    var devolverEm by remember { mutableStateOf<Emprestimo?>(null) }
    var confirmarDevolverTodos by rememberSaveable { mutableStateOf(false) }
    var problemaDescricao by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    // ETAPA 6B: com "Avisos ficam na tela" ligado, o aviso só some quando o professor toca em Fechar.
    LaunchedEffect(mensagem) {
        mensagem?.let { texto ->
            val persistente = acessibilidade.avisosPersistentes
            snackbarHostState.showSnackbar(
                message = texto,
                actionLabel = if (persistente) "Fechar" else null,
                duration = if (persistente) SnackbarDuration.Indefinite else SnackbarDuration.Short
            )
            mensagem = null
        }
    }
    LaunchedEffect(loanState.notice) { loanState.notice?.let { mensagem = it; equipmentViewModel.carregar(silencioso = true) } }
    // ETAPA 6A: atualização manual dos dados (Configurações > Atualizar dados).
    var puxando by remember { mutableStateOf(false) }
    var atualizando by remember { mutableStateOf(false) }
    var resultadoAtualizacao by remember { mutableStateOf<ResultadoAtualizacao?>(null) }
    var erroEmprestimoTratado by remember { mutableStateOf<String?>(null) }
    val escopoAtualizacao = rememberCoroutineScope()
    // O erro de empréstimo só vira snackbar fora da atualização manual (que mostra o próprio resultado).
    LaunchedEffect(loanState.error) {
        val erro = loanState.error
        if (erro == null) erroEmprestimoTratado = null
        else if (!atualizando && !puxando && erro != erroEmprestimoTratado) mensagem = erro
    }
    // O resultado não fica "velho": some ao trocar de tela.
    LaunchedEffect(aba) { if (!atualizando) resultadoAtualizacao = null }
    // Preferência desligada: o aviso de Configurações desaparece sozinho. Ligada: fica até o professor fechar.
    LaunchedEffect(resultadoAtualizacao, acessibilidade.avisosPersistentes) {
        if (resultadoAtualizacao != null && !acessibilidade.avisosPersistentes) {
            delay(6000)
            resultadoAtualizacao = null
        }
    }
    // Puxar para atualizar (deslizar a tela para baixo nas abas Início, Pedir e Atividade).
    fun atualizarPuxando() {
        if (puxando || atualizando) return
        puxando = true
        escopoAtualizacao.launch {
            val trabalhos = buildList<kotlinx.coroutines.Job> {
                add(equipmentViewModel.carregar())
                add(defectViewModel.carregar())
                if (userId.isNotBlank()) add(loanViewModel.carregar(userId))
            }
            trabalhos.joinAll()
            val falhou = userId.isBlank() ||
                    equipmentViewModel.state.value.error != null ||
                    defectViewModel.state.value.error != null ||
                    loanViewModel.state.value.error != null
            erroEmprestimoTratado = loanViewModel.state.value.error
            if (falhou) mensagem = "Não foi possível atualizar. Verifique a internet e tente de novo."
            puxando = false
        }
    }
    fun atualizarDados() {
        if (atualizando) return // evita várias atualizações ao mesmo tempo
        atualizando = true
        resultadoAtualizacao = null
        escopoAtualizacao.launch {
            val trabalhos = buildList<kotlinx.coroutines.Job> {
                add(equipmentViewModel.carregar())
                add(defectViewModel.carregar())
                if (userId.isNotBlank()) add(loanViewModel.carregar(userId))
            }
            trabalhos.joinAll()
            val semSessao = userId.isBlank()
            val falhou = semSessao ||
                    equipmentViewModel.state.value.error != null ||
                    defectViewModel.state.value.error != null ||
                    loanViewModel.state.value.error != null
            erroEmprestimoTratado = loanViewModel.state.value.error
            resultadoAtualizacao = when {
                semSessao -> ResultadoAtualizacao(false, "Não foi possível atualizar. Entre novamente na sua conta.")
                falhou -> ResultadoAtualizacao(false, "Não foi possível atualizar. Verifique a internet e tente de novo.")
                else -> ResultadoAtualizacao(true, "Dados atualizados.")
            }
            atualizando = false
        }
    }
    // Na tela de Configurações, o botão Voltar do celular volta para a aba de onde o professor veio.
    BackHandler(enabled = aba == "Configurações" && !scannerVisible) { aba = abaAnterior }
    if (mostrarAjuda) DialogoAjuda(
        atualizando = atualizando,
        resultado = resultadoAtualizacao,
        avisoPersistente = acessibilidade.avisosPersistentes,
        alturaMinima = if (acessibilidade.toqueAmpliado) 64.dp else 52.dp,
        onAtualizar = { atualizarDados() },
        onFecharAviso = { resultadoAtualizacao = null },
        onInformarProblema = { mostrarAjuda = false; problemaCodigo = ""; problemaDescricao = ""; mostrarProblema = true },
        onFechar = { mostrarAjuda = false }
    )
    if (mostrarSuporte) AlertDialog(
        onDismissRequest = { mostrarSuporte = false },
        title = { Text("Suporte técnico") },
        text = { Text("Para ajuda técnica com o aplicativo, procure a administração da escola. Se o problema for com um equipamento, você pode registrá-lo agora.") },
        confirmButton = {
            TextButton(onClick = { mostrarSuporte = false; problemaCodigo = ""; problemaDescricao = ""; mostrarProblema = true }) { Text("Informar problema") }
        },
        dismissButton = { TextButton(onClick = { mostrarSuporte = false }) { Text("Fechar") } }
    )
    if (scannerVisible) {
        BarcodeScannerScreen(onClose = { scannerVisible = false }, onCodeRead = { codigo ->
            equipmentViewModel.carregar(silencioso = true)
            if (userId.isNotBlank()) loanViewModel.carregar(userId, silencioso = true)
            codigoEscaneado = codigo; scannerVisible = false
        })
    } else Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .14f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Devices, contentDescription = "Empresta Aê", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(9.dp).size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Empresta Aê", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (nomeProfessor.isBlank()) "Professor" else nomeProfessor, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (LocalAltoContraste.current) 1f else .82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } }, navigationIcon = {
            if (aba == "Configurações") IconButton(onClick = { aba = abaAnterior }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        }, actions = {
            if (aba == "Configurações") {
                Box {
                    IconButton(onClick = { menuAberto = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                        DropdownMenuItem(text = { Text("Ajuda") }, onClick = { menuAberto = false; mostrarAjuda = true })
                        DropdownMenuItem(text = { Text("Suporte técnico") }, onClick = { menuAberto = false; mostrarSuporte = true })
                    }
                }
            } else {
                IconButton(onClick = { abaAnterior = aba; aba = "Configurações"; pesquisa = "" }) {
                    Icon(Icons.Default.Settings, contentDescription = "Configurações")
                }
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary))
    }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, windowInsets = NavigationBarDefaults.windowInsets) {
            listOf("Início" to "Início", "Pedir" to "Pedir", "Atividade" to "Atividade").forEach { (rótulo, destino) ->
                val icon = when (destino) { "Início" -> Icons.Default.Home; "Pedir" -> Icons.Default.QrCode2; else -> Icons.AutoMirrored.Filled.Assignment }
                NavigationBarItem(selected = aba == destino, onClick = { aba = destino; pesquisa = "" }, icon = { Icon(icon, contentDescription = null) }, label = { Text(rótulo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxHeight().align(Alignment.TopCenter)) {
                if (aba == "Configurações") Mais(
                    modoVisual = modoVisual,
                    onModoVisual = onModoVisual,
                    acessibilidade = acessibilidade,
                    onAcessibilidade = onAcessibilidade,
                    onSair = onSair
                ) else PullToRefreshBox(isRefreshing = puxando, onRefresh = { atualizarPuxando() }, modifier = Modifier.fillMaxSize()) {
                    when (aba) {
                        "Início" -> Inicio(banco, nomeProfessor, onIr = { aba = it }, onEscanear = { scannerVisible = true }, onDevolver = { devolverEm = it }, onDevolverTodos = { confirmarDevolverTodos = true }, onProblema = { c -> problemaCodigo = c; problemaDescricao = ""; mostrarProblema = true }, carregando = equipamentosState.items.isEmpty())
                        "Pedir" -> Emprestar(onEscanear = { scannerVisible = true }, onBuscarCodigo = { codigoEscaneado = it },
                            ativos = banco.emprestimos.filter { it.devolucao == null }, equipamentos = banco.equipamentos,
                            onDevolver = { devolverEm = it }, onDevolverTodos = { confirmarDevolverTodos = true }, onProblemaEquipamento = { c -> problemaCodigo = c; problemaDescricao = ""; mostrarProblema = true })
                        "Atividade" -> Column {
                            if (loanState.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            loanState.error?.let { Text("Falha ao consultar histórico: $it", color = MaterialTheme.colorScheme.error) }
                            Historico(banco, pesquisa, { pesquisa = it }, filtroAtividade, { filtroAtividade = it }, nomeProfessor, onDevolver = { em, _ ->
                                val id = em.id.toLongOrNull()
                                if (id == null || userId.isBlank()) mensagem = "Empréstimo ou sessão inválidos."
                                else loanViewModel.devolver(id, userId)
                            })
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    if (mostrarProblema) AlertDialog(onDismissRequest = { mostrarProblema = false }, title = { Text("Registrar problema") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(problemaCodigo, { problemaCodigo = it.uppercase() }, label = { Text("Código do equipamento") }, singleLine = true)
        OutlinedTextField(problemaDescricao, { problemaDescricao = it }, label = { Text("Descreva o problema") }, minLines = 2)
    } }, confirmButton = { TextButton(onClick = {
        val codigo = problemaCodigo.trim(); val existe = banco.equipamentos.any { it.codigo.equals(codigo, true) }
        if (!existe || problemaDescricao.isBlank()) mensagem = "Informe um equipamento cadastrado e descreva o problema."
        else {
            val equipamento = banco.equipamentos.first { it.codigo.equals(codigo, true) }
            val userId = prefs.getString("supabase_user_id", null)
            if (userId == null || equipamento.id.toLongOrNull() == null) mensagem = "A sessão ou identificação do equipamento não está disponível."
            else defectViewModel.registrar(DefeitoInsert(equipamento.id.toLong(), userId, "Outros", problemaDescricao.trim())) {
                mostrarProblema = false; problemaCodigo = ""; problemaDescricao = ""; mensagem = "Defeito registrado no Supabase."
            }
        }
    }) { Text("Registrar") } }, dismissButton = { TextButton(onClick = { mostrarProblema = false }) { Text("Cancelar") } })

    if (confirmarDevolverTodos) {
        val ativosAgora = banco.emprestimos.filter { it.devolucao == null }
        AlertDialog(
            onDismissRequest = { confirmarDevolverTodos = false },
            title = { Text("Devolver todos?") },
            text = { Text("Confirma a devolução dos ${ativosAgora.size} equipamentos que estão com você?") },
            confirmButton = { Button(onClick = {
                val ids = ativosAgora.mapNotNull { it.id.toLongOrNull() }
                if (ids.isEmpty() || userId.isBlank()) mensagem = "Empréstimo ou sessão inválidos."
                else loanViewModel.devolverTodos(ids, userId)
                confirmarDevolverTodos = false
            }) { Text("Devolver todos") } },
            dismissButton = { TextButton(onClick = { confirmarDevolverTodos = false }) { Text("Cancelar") } }
        )
    }

    devolverEm?.let { em ->
        val eq = banco.equipamentos.firstOrNull { it.codigo == em.codigos.firstOrNull() }
        AlertDialog(
            onDismissRequest = { devolverEm = null },
            title = { Text("Confirmar devolução") },
            text = { Text("Confirma a devolução de ${eq?.nome ?: "este equipamento"}${em.codigos.firstOrNull()?.let { " ($it)" } ?: ""}?") },
            confirmButton = { Button(onClick = {
                val id = em.id.toLongOrNull()
                if (id == null || userId.isBlank()) mensagem = "Empréstimo ou sessão inválidos." else loanViewModel.devolver(id, userId)
                devolverEm = null
            }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { devolverEm = null }) { Text("Voltar") } }
        )
    }

    equipamentoEtiqueta?.let { equipamento ->
        EquipmentLabelDialog(
            equipment = equipamento,
            onDismiss = { equipamentoEtiqueta = null },
            onShare = { label ->
                runCatching { shareEquipmentLabel(context, equipamento, label) }
                    .onSuccess { equipamentoEtiqueta = null }
                    .onFailure { mensagem = it.localizedMessage ?: "Não foi possível compartilhar a etiqueta." }
            },
            onPrint = { label ->
                runCatching { printEquipmentLabel(context, equipamento, label) }
                    .onSuccess { equipamentoEtiqueta = null }
                    .onFailure { mensagem = it.localizedMessage ?: "Não foi possível abrir a impressão." }
            }
        )
    }

    codigoEscaneado?.let { codigo ->
        val equipamento = EquipmentCodeLookup.find(banco.equipamentos, codigo)
        val meuEmprestimo = equipamento?.let { eq -> banco.emprestimos.firstOrNull { it.devolucao == null && eq.codigo in it.codigos } }
        val ocupado = loanState.loading
        AlertDialog(
            onDismissRequest = { codigoEscaneado = null },
            title = { Text(if (equipamento == null) "Equipamento não encontrado" else "Equipamento identificado") },
            text = {
                if (equipamento == null) Text("Equipamento não encontrado. Verifique se o código está cadastrado no sistema.\n\nCódigo lido: $codigo")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(equipamento.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Patrimônio / código: ${equipamento.codigo}")
                    Text("Categoria: ${equipamento.tipo}")
                    StatusPill(if (meuEmprestimo != null) "Com você" else equipamento.status)
                    when {
                        meuEmprestimo != null -> Text("Este equipamento está emprestado para você.")
                        equipamento.status == "Disponível" -> Text("Disponível para empréstimo.")
                        equipamento.status == "Manutenção" -> Text("Em manutenção. Não pode ser emprestado agora.")
                        else -> Text("Em uso por outra pessoa. Não pode ser emprestado agora.")
                    }
                    // Em manutenção o defeito já foi informado: não oferece "Informar problema" de novo.
                    if (equipamento.status != "Manutenção") OutlinedButton(onClick = {
                        problemaCodigo = equipamento.codigo; problemaDescricao = ""; mostrarProblema = true; codigoEscaneado = null
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ReportProblem, null); Spacer(Modifier.width(8.dp)); Text("Informar problema") }
                }
            },
            confirmButton = {
                if (equipamento == null) TextButton(onClick = { codigoEscaneado = null }) { Text("Fechar") }
                else if (meuEmprestimo != null) Button(onClick = {
                    val idEmprestimo = meuEmprestimo.id.toLongOrNull()
                    if (idEmprestimo == null || userId.isBlank()) mensagem = "Empréstimo ou sessão inválidos."
                    else { loanViewModel.devolver(idEmprestimo, userId); codigoEscaneado = null }
                }, enabled = !ocupado) { Text("Devolver equipamento") }
                else if (EquipmentCodeLookup.canRequest(equipamento)) Button(onClick = {
                    val idEquipamento = equipamento.id.toLongOrNull()
                    if (idEquipamento == null || userId.isBlank()) mensagem = "Entre novamente para realizar empréstimos."
                    else { loanViewModel.emprestar(idEquipamento, userId); codigoEscaneado = null }
                }, enabled = !ocupado) { Text("Pedir emprestado") }
                else TextButton(onClick = { codigoEscaneado = null }) { Text("Fechar") }
            },
            dismissButton = {
                if (equipamento != null && (meuEmprestimo != null || EquipmentCodeLookup.canRequest(equipamento))) TextButton(onClick = { codigoEscaneado = null }) { Text("Cancelar") }
            }
        )
    }

}

@Composable
private fun Inicio(banco: BancoLocal, nomeProfessor: String, onIr: (String) -> Unit, onEscanear: () -> Unit, onDevolver: (Emprestimo) -> Unit, onDevolverTodos: () -> Unit, onProblema: (String) -> Unit, carregando: Boolean = false) {
    val disponiveis = banco.equipamentos.count { it.status == "Disponível" }
    val notebooks = banco.equipamentos.count { it.tipo.contains("notebook", ignoreCase = true) }
    val tablets = banco.equipamentos.count { it.tipo.contains("tablet", ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(10.dp).size(23.dp))
            }
            Text(if (nomeProfessor.isBlank()) "Olá, professor(a)!" else "Olá, ${nomeProfessor.substringBefore(' ')}!", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Acompanhe os equipamentos da escola e gerencie seus empréstimos em um só lugar.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (LocalAltoContraste.current) 1f else .88f), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onEscanear,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
            ) { Icon(Icons.Default.QrCode2, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Escanear e pedir equipamento", fontWeight = FontWeight.Bold) }
        } } }
        item { Text("Equipamentos da escola", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResumoCard("Notebooks", if (carregando) "–" else notebooks.toString(), MaterialTheme.colorScheme.primary, Icons.Default.Devices, Modifier.weight(1f))
            ResumoCard("Tablets", if (carregando) "–" else tablets.toString(), MaterialTheme.colorScheme.primary, Icons.Default.Devices, Modifier.weight(1f))
            ResumoCard("Livres", if (carregando) "–" else disponiveis.toString(), MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle, Modifier.weight(1f))
        } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Seus empréstimos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { onIr("Atividade") }) { Text("Ver atividade") }
        } }
        val ativos = banco.emprestimos.filter { it.devolucao == null }
        if (ativos.isEmpty() && carregando) item { Text("Carregando seus empréstimos…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else if (ativos.isEmpty()) item { EmptyCard("Tudo em dia", if (nomeProfessor.isBlank()) "Solicite equipamentos quando precisar." else "Você não tem equipamento emprestado no momento.", Icons.Default.Inbox) }
        if (ativos.size > 1) item { OutlinedButton(onClick = onDevolverTodos, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Devolver todos (${ativos.size})") } }
        items(ativos.take(4), key = { it.id }) { AtivoCard(it, banco.equipamentos, onDevolver, onProblema) }
        item { Text("Acesso rápido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onIr("Atividade") }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Icon(Icons.AutoMirrored.Filled.Assignment, null); Spacer(Modifier.width(7.dp)); Text("Minhas atividades") }
        } }
    }
}

@Composable
private fun ResumoCard(titulo: String, valor: String, cor: androidx.compose.ui.graphics.Color, icone: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, border = BorderStroke(1.dp, cor.copy(alpha = if (LocalAltoContraste.current) 1f else .22f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icone, null, tint = cor, modifier = Modifier.size(18.dp))
                Text(valor, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Emprestar(onEscanear: () -> Unit, onBuscarCodigo: (String) -> Unit, ativos: List<Emprestimo>, equipamentos: List<Equipamento>, onDevolver: (Emprestimo) -> Unit, onDevolverTodos: () -> Unit, onProblemaEquipamento: (String) -> Unit) {
    var codigoManual by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TituloSecao("Pedir equipamento", "Escaneie o QR Code ou o código de barras do equipamento que você vai usar.") }
        item { Button(onClick = onEscanear, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Default.QrCode2, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Escanear equipamento") } }
        // Digitar o código fica logo abaixo do botão de escanear.
        item { Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sem câmera ou etiqueta ilegível?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Digite o código impresso na etiqueta do equipamento.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(codigoManual, { codigoManual = it }, Modifier.fillMaxWidth(), label = { Text("Código do equipamento") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (codigoManual.isNotBlank()) onBuscarCodigo(codigoManual.trim()) }))
                OutlinedButton(onClick = { onBuscarCodigo(codigoManual.trim()) }, enabled = codigoManual.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Buscar equipamento") }
            }
        } }
        item { Text("Com você agora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (ativos.isEmpty()) item { Text("Nenhum equipamento com você no momento.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // Com 2 ou mais equipamentos, aparece a opção de devolver todos de uma vez.
        if (ativos.size > 1) item { OutlinedButton(onClick = onDevolverTodos, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Devolver todos (${ativos.size})") } }
        items(ativos, key = { it.id }) { AtivoCard(it, equipamentos, onDevolver, onProblemaEquipamento) }
    }
}

@Composable
private fun Inventario(banco: BancoLocal, pesquisa: String, onPesquisa: (String) -> Unit, filtro: String, onFiltro: (String) -> Unit, onReportar: (String) -> Unit, onEscanear: () -> Unit, onEtiqueta: (Equipamento) -> Unit) {
    val lista = banco.equipamentos.filter { eq -> "${eq.nome} ${eq.codigo} ${eq.tipo} ${eq.status}".contains(pesquisa, true) && (filtro == "Todos" || eq.status == filtro) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TituloSecao("Equipamentos", "Consulte o que está livre para uso.") }
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(pesquisa, onPesquisa, Modifier.weight(1f), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Buscar por nome ou código") }, singleLine = true)
            IconButton(onClick = onEscanear, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Devices, contentDescription = "Escanear equipamento", tint = MaterialTheme.colorScheme.primary) }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Todos", "Disponível", "Em uso", "Manutenção").forEach { estado ->
            val rótulo = when (estado) { "Disponível" -> "Livres"; "Manutenção" -> "Reparo"; else -> estado }
            FilterChip(selected = filtro == estado, onClick = { onFiltro(estado) }, label = { Text(rótulo, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
        } } }
        item { Text("${lista.size} equipamento(s)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(lista, key = { it.id }) { eq -> Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(22.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(eq.nome, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge); Text("${eq.tipo}  •  ${eq.codigo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                StatusPill(eq.status)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onReportar(eq.codigo) }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) { Icon(Icons.Default.ReportProblem, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Informar problema") }
                FilledTonalButton(onClick = { onEtiqueta(eq) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Etiqueta") }
            }
        } } }
        if (lista.isEmpty()) item { EmptyCard("Nenhum equipamento encontrado", "Altere o filtro ou tente outro nome ou código.") }
        if (banco.problemas.isNotEmpty()) {
            item { Text("Problemas relatados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(banco.problemas, key = { "problem-${it.id}" }) { problem ->
                val equipment = banco.equipamentos.firstOrNull { it.codigo == problem.codigo }
                Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(equipment?.nome ?: "Equipamento ${problem.codigo}", fontWeight = FontWeight.SemiBold)
                                Text("${problem.codigo} · Relatado ${problem.data}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            equipment?.let { StatusPill(it.status) }
                        }
                        Text(problem.descricao, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Historico(banco: BancoLocal, pesquisa: String, onPesquisa: (String) -> Unit, filtro: String, onFiltro: (String) -> Unit, nomeProfessor: String, onDevolver: (Emprestimo, String) -> Unit) {
    val lista = banco.emprestimos.filter { em ->
        val nomesItens = em.codigos.mapNotNull { codigo -> banco.equipamentos.firstOrNull { it.codigo == codigo }?.nome }.joinToString(" ")
        "${em.codigos.joinToString()} $nomesItens".contains(pesquisa, true) && when (filtro) { "Em aberto" -> em.devolucao == null; "Concluídos" -> em.devolucao != null; else -> true }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TituloSecao("Minhas atividades", "Acompanhe pedidos, itens emprestados e devoluções.") }
        item { OutlinedTextField(pesquisa, onPesquisa, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Buscar equipamento") }, singleLine = true) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Todos", "Em aberto", "Concluídos").forEach { estado -> FilterChip(selected = filtro == estado, onClick = { onFiltro(estado) }, label = { Text(estado) }) } } }
        if (lista.isEmpty()) item { EmptyActivityCard("Nenhuma atividade ainda", "Seus pedidos e devoluções aparecerão aqui.") }
        items(lista, key = { it.id }) { em ->
            var codigoParaConfirmar by remember(em.id) { mutableStateOf<String?>(null) }
            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("Empréstimo #${em.id}", fontWeight = FontWeight.Bold); Text("${em.inicio}  •  ${em.codigos.size} item(ns)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; StatusPill(if (em.devolucao == null) "Em aberto" else "Concluído") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                em.codigos.forEach { codigo ->
                    val item = banco.equipamentos.firstOrNull { it.codigo == codigo }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) { Text(item?.nome ?: "Equipamento", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium); Text(codigo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (codigo in em.devolvidos) StatusPill("Devolvido") else TextButton(onClick = { codigoParaConfirmar = codigo }, enabled = em.devolucao == null) { Text("Devolver") }
                    }
                }
                if (em.devolucao != null) Text("Devolvido em ${em.devolucao} por ${em.devolvidoPor ?: "você"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            codigoParaConfirmar?.let { codigo -> AlertDialog(onDismissRequest = { codigoParaConfirmar = null }, title = { Text("Confirmar devolução") }, text = { Text("Confirma a devolução de ${banco.equipamentos.firstOrNull { it.codigo == codigo }?.nome ?: codigo} ($codigo)?") }, confirmButton = { Button(onClick = { codigoParaConfirmar = null; onDevolver(em, codigo) }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { codigoParaConfirmar = null }) { Text("Voltar") } }) }
        }
        if (banco.problemas.isNotEmpty()) item { Text("Problemas informados (${banco.problemas.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(banco.problemas, key = { it.id }) { p -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("${p.codigo} • ${p.data}", fontWeight = FontWeight.SemiBold); Text(p.descricao) } } }
    }
}

@Composable
private fun DialogoAjuda(
    atualizando: Boolean,
    resultado: ResultadoAtualizacao?,
    avisoPersistente: Boolean,
    alturaMinima: Dp,
    onAtualizar: () -> Unit,
    onFecharAviso: () -> Unit,
    onInformarProblema: () -> Unit,
    onFechar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Ajuda") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Orientações rápidas para o dia a dia.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Para pedir um equipamento, toque em Pedir e escaneie o QR Code.", style = MaterialTheme.typography.bodyMedium)
                Text("• Para devolver, toque em Devolver no equipamento que está com você.", style = MaterialTheme.typography.bodyMedium)
                Text("• Se alguma informação parecer errada, toque em Atualizar dados, logo abaixo.", style = MaterialTheme.typography.bodyMedium)
                Text("• Se o problema continuar, procure a administração da escola.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAtualizar, enabled = !atualizando, modifier = Modifier.fillMaxWidth().heightIn(min = alturaMinima)) {
                    if (atualizando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    else Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (atualizando) "Atualizando…" else "Atualizar dados")
                }
                resultado?.let { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (r.sucesso) Icons.Default.CheckCircle else Icons.Default.ReportProblem,
                            contentDescription = null,
                            tint = if (r.sucesso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            r.texto,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (r.sucesso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (avisoPersistente) {
                            TextButton(
                                onClick = onFecharAviso,
                                modifier = Modifier.heightIn(min = alturaMinima).semantics { contentDescription = "Fechar aviso" }
                            ) { Text("Fechar") }
                        }
                    }
                }
                OutlinedButton(onClick = onInformarProblema, modifier = Modifier.fillMaxWidth().heightIn(min = alturaMinima)) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Informar problema com um equipamento")
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )
}

@Composable
private fun Mais(
    modoVisual: String,
    onModoVisual: (String) -> Unit,
    acessibilidade: PreferenciasAcessibilidade,
    onAcessibilidade: (PreferenciasAcessibilidade) -> Unit,
    onSair: () -> Unit
) {
    val alturaMinima = if (acessibilidade.toqueAmpliado) 64.dp else 52.dp
    // ETAPA 6C: confirmação antes de sair da conta.
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }
    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Sair da conta?") },
            text = { Text("Tem certeza de que deseja sair da sua conta?") },
            confirmButton = {
                TextButton(
                    onClick = { confirmarSaida = false; onSair() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { confirmarSaida = false }) { Text("Cancelar") } }
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TituloSecao("Configurações", "Deixe o aplicativo do seu jeito.") }

        // A) Acessibilidade
        item {
            SecaoConfig("Acessibilidade", "Ajustes para ler e tocar com mais facilidade.") {
                Text("Tamanho do texto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TamanhoTexto.entries.forEach { opcao ->
                    OpcaoUnica(opcao.rotulo, null, acessibilidade.tamanhoTexto == opcao, alturaMinima) {
                        onAcessibilidade(acessibilidade.copy(tamanhoTexto = opcao))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinhaChave("Contraste alto", "Deixa textos e bordas mais fortes.", acessibilidade.altoContraste, alturaMinima) {
                    onAcessibilidade(acessibilidade.copy(altoContraste = it))
                }
                LinhaChave("Aumentar áreas de toque", "Deixa os botões maiores e mais fáceis de tocar.", acessibilidade.toqueAmpliado, alturaMinima) {
                    onAcessibilidade(acessibilidade.copy(toqueAmpliado = it))
                }
                LinhaChave("Avisos ficam na tela", "Os avisos só somem quando você fechar.", acessibilidade.avisosPersistentes, alturaMinima) {
                    onAcessibilidade(acessibilidade.copy(avisosPersistentes = it))
                }
            }
        }

        // B) Aparência
        item {
            SecaoConfig("Aparência", "Escolha como prefere ver o aplicativo.") {
                listOf(
                    Triple("Aparelho", "Padrão", "Acompanha o tema do celular."),
                    Triple("Claro", "Claro", null),
                    Triple("Escuro", "Escuro", null)
                ).forEach { (valor, rotulo, detalhe) ->
                    val icone = when (valor) { "Claro" -> Icons.Default.LightMode; "Escuro" -> Icons.Default.DarkMode; else -> Icons.Default.Settings }
                    OpcaoUnica(rotulo, detalhe, modoVisual == valor, alturaMinima, icone) { onModoVisual(valor) }
                }
                Text("O contraste alto pode ser usado com qualquer tema. Ligue em Acessibilidade.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // D) Sobre
        item {
            SecaoConfig("Sobre") {
                Text("Empresta Aê", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Controle de equipamentos escolares", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Versão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // E) Sair
        item {
            OutlinedButton(
                onClick = { confirmarSaida = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = alturaMinima),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sair da conta")
            }
        }
    }
}

@Composable
private fun SecaoConfig(titulo: String, descricao: String? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            if (descricao != null) Text(descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            conteudo()
        }
    }
}

/** Opção de escolha única (como botão de rádio): a linha inteira é tocável e o leitor de tela anuncia "selecionado". */
@Composable
private fun OpcaoUnica(
    rotulo: String,
    detalhe: String?,
    selecionado: Boolean,
    alturaMinima: Dp,
    icone: ImageVector? = null,
    aoSelecionar: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = alturaMinima).selectable(selected = selecionado, role = Role.RadioButton, onClick = aoSelecionar),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selecionado, onClick = null)
        if (icone != null) Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = MaterialTheme.typography.bodyLarge)
            if (detalhe != null) Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Opção liga/desliga: a linha inteira é tocável e o leitor de tela anuncia "ligado/desligado". */
@Composable
private fun LinhaChave(titulo: String, detalhe: String, marcado: Boolean, alturaMinima: Dp, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = alturaMinima).toggleable(value = marcado, role = Role.Switch, onValueChange = aoMudar),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = marcado, onCheckedChange = null)
    }
}

@Composable
private fun EmprestimoCard(em: Emprestimo, equipamentos: List<Equipamento>, onAbrir: () -> Unit) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text("Empréstimo", fontWeight = FontWeight.SemiBold); Text("Solicitado em ${em.inicio}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                StatusPill("Em uso")
            }
            em.codigos.forEach { codigo ->
                val item = equipamentos.firstOrNull { it.codigo == codigo }
                Text("${item?.nome ?: "Equipamento"} • $codigo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            TextButton(onClick = onAbrir, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) { Text("Acompanhar devolução") }
        }
    }
}

@Composable
private fun AtivoCard(em: Emprestimo, equipamentos: List<Equipamento>, onDevolver: (Emprestimo) -> Unit, onProblema: (String) -> Unit) {
    val codigo = em.codigos.firstOrNull().orEmpty()
    val item = equipamentos.firstOrNull { it.codigo == codigo }
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item?.nome ?: "Equipamento", fontWeight = FontWeight.SemiBold)
                    Text("$codigo  •  desde ${em.inicio}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill("Em uso")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDevolver(em) }, modifier = Modifier.weight(1f)) { Text("Devolver") }
                OutlinedButton(onClick = { onProblema(codigo) }, modifier = Modifier.weight(1f)) { Text("Informar problema") }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val cor = when (status) { "Disponível", "Devolvido", "Concluído" -> AvailableGreen; "Manutenção", "Em aberto" -> PendingOrange; else -> MaterialTheme.colorScheme.primary }
    Surface(color = cor.copy(alpha = .13f), shape = RoundedCornerShape(8.dp), border = if (LocalAltoContraste.current) BorderStroke(1.dp, cor) else null) { Text(status, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = cor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun TituloSecao(titulo: String, descricao: String) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun EmptyCard(titulo: String, detalhe: String, icone: ImageVector = Icons.Default.Inbox) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) { Icon(icone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(24.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(titulo, fontWeight = FontWeight.SemiBold); Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun EmptyActivityCard(titulo: String, detalhe: String) {
    Card(
        Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp).size(42.dp))
            }
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(detalhe, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun EquipmentLabelDialog(
    equipment: Equipamento,
    onDismiss: () -> Unit,
    onShare: (EquipmentLabel) -> Unit,
    onPrint: (EquipmentLabel) -> Unit
) {
    var format by remember(equipment.id) { mutableStateOf(LabelFormat.QR) }
    val generated = remember(equipment.id, equipment.codigo, equipment.nome, equipment.tipo, format) {
        runCatching { EquipmentLabelGenerator.generate(equipment, format) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiqueta do equipamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(equipment.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("Código ${equipment.codigo} · ${equipment.tipo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = format == LabelFormat.QR, onClick = { format = LabelFormat.QR }, label = { Text("QR Code") })
                    FilterChip(selected = format == LabelFormat.CODE_128, onClick = { format = LabelFormat.CODE_128 }, label = { Text("Código 128") })
                }
                generated.onSuccess { label ->
                    androidx.compose.foundation.Image(
                        bitmap = label.bitmap.asImageBitmap(),
                        contentDescription = "Prévia da etiqueta ${equipment.codigo} em ${format.title}",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    Text(
                        if (format == LabelFormat.QR) "O QR identifica este item pelo seu ID interno permanente." else "O código de barras representa ${equipment.codigo}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }.onFailure { error ->
                    Text(error.message ?: "Não foi possível gerar esta etiqueta.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { generated.getOrNull()?.let(onShare) }, enabled = generated.isSuccess) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Compartilhar")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { generated.getOrNull()?.let(onPrint) }, enabled = generated.isSuccess) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Imprimir")
                }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        }
    )
}