package com.example.emprestaa.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object SignedOut : AuthState
    data object Loading : AuthState
    data class SignedIn(val user: SessaoUsuario) : AuthState
    data class Error(val message: String) : AuthState
}

/** Troca mensagens técnicas (em inglês) por textos claros em português. */
internal fun traduzirErro(erro: Throwable): String {
    val completo = (erro.message ?: "").lowercase()
    return when {
        "invalid login credentials" in completo || "invalid_credentials" in completo -> "E-mail ou senha incorretos."
        "email not confirmed" in completo || "email_not_confirmed" in completo -> "E-mail ainda não confirmado. Procure a administração da escola."
        erro is java.io.IOException || "unable to resolve host" in completo || "timeout" in completo || "failed to connect" in completo ->
            "Sem conexão com a internet. Verifique sua rede e tente novamente."
        completo.isBlank() -> "Não foi possível concluir a operação."
        else -> erro.message.orEmpty().lineSequence().first() // só a 1ª linha (sem URL/cabeçalhos)
    }
}

class AuthViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {
    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state = _state.asStateFlow()
    private val _sessaoPerdida = MutableStateFlow(false)
    /** true quando o Supabase não tem mais sessão válida (ex.: refresh token recusado). */
    val sessaoPerdida = _sessaoPerdida.asStateFlow()

    init {
        viewModelScope.launch { runCatching { repository.observarSessao { perdida -> _sessaoPerdida.value = perdida } } }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _state.value = AuthState.Loading
        runCatching { repository.entrar(email.trim(), password.trim()) }
            .onSuccess { _state.value = AuthState.SignedIn(it) }
            .onFailure { _state.value = AuthState.Error(traduzirErro(it)) }
    }
    fun logout() = viewModelScope.launch {
        runCatching { repository.sair() } // sem internet, o app ainda sai
        _state.value = AuthState.SignedOut
    }
    fun reset(email: String) = viewModelScope.launch {
        _state.value = AuthState.Loading
        runCatching { repository.recuperar(email.trim()) }
            .onSuccess { _state.value = AuthState.Error("Se o e-mail estiver cadastrado, você receberá as instruções de recuperação.") }
            .onFailure { _state.value = AuthState.Error(traduzirErro(it)) }
    }
}

data class EquipmentUiState(val loading: Boolean = false, val items: List<EquipamentoRow> = emptyList(), val error: String? = null)
class EquipmentViewModel(private val repository: EquipamentoRepository = EquipamentoRepository()) : ViewModel() {
    private val _state = MutableStateFlow(EquipmentUiState())
    val state = _state.asStateFlow()
    fun carregar(silencioso: Boolean = false) = viewModelScope.launch {
        if (!silencioso) _state.value = _state.value.copy(loading = true, error = null)
        runCatching { repository.listar() }.onSuccess { _state.value = EquipmentUiState(items = it) }
            .onFailure { if (!silencioso) _state.value = _state.value.copy(loading = false, error = traduzirErro(it)) }
    }
}

data class DefectUiState(val loading: Boolean = false, val error: String? = null, val success: String? = null, val rows: List<DefeitoRow> = emptyList())
class DefectViewModel(private val repository: DefeitoRepository = DefeitoRepository()) : ViewModel() {
    private val _state = MutableStateFlow(DefectUiState())
    val state = _state.asStateFlow()
    fun carregar(silencioso: Boolean = false) = viewModelScope.launch {
        if (!silencioso) _state.value = _state.value.copy(loading = true, error = null)
        runCatching { repository.listar() }.onSuccess { _state.value = _state.value.copy(loading = false, error = null, rows = it) }
            .onFailure { if (!silencioso) _state.value = _state.value.copy(loading = false, error = traduzirErro(it)) }
    }
    fun atualizarStatus(id: Long, status: String) = viewModelScope.launch {
        val anterior = _state.value
        _state.value = anterior.copy(loading = true, error = null)
        runCatching { repository.atualizarStatus(id, status); repository.listar() }
            .onSuccess { _state.value = DefectUiState(rows = it) }
            .onFailure { _state.value = anterior.copy(loading = false, error = traduzirErro(it)) }
    }
    fun registrar(row: DefeitoInsert, onDone: () -> Unit) = viewModelScope.launch {
        val anterior = _state.value
        _state.value = anterior.copy(loading = true, error = null, success = null)
        runCatching { repository.registrar(row); repository.listar() }
            .onSuccess { _state.value = DefectUiState(rows = it, success = "Defeito registrado no sistema."); onDone() }
            .onFailure { _state.value = anterior.copy(loading = false, error = traduzirErro(it)) }
    }
}

data class LoanUiState(val loading: Boolean = false, val rows: List<EmprestimoRow> = emptyList(), val error: String? = null, val notice: String? = null)
class LoanViewModel(private val repository: EmprestimoRepository = EmprestimoRepository()) : ViewModel() {
    private val _state = MutableStateFlow(LoanUiState())
    val state = _state.asStateFlow()
    fun carregar(uid: String, gestao: Boolean = false, silencioso: Boolean = false) = viewModelScope.launch {
        if (!silencioso) _state.value = _state.value.copy(loading = true, error = null)
        runCatching { if (gestao) repository.listarGestao() else repository.historico(uid) }
            .onSuccess { _state.value = LoanUiState(rows = it) }
            .onFailure { if (!silencioso) _state.value = LoanUiState(rows = _state.value.rows, error = traduzirErro(it)) }
    }
    fun emprestar(equipmentId: Long, uid: String, gestao: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null, notice = null)
        runCatching { repository.retirar(equipmentId); if (gestao) repository.listarGestao() else repository.historico(uid) }
            .onSuccess { _state.value = LoanUiState(rows = it, notice = "Empréstimo registrado. Quando terminar, devolva em Atividade.") }
            .onFailure { _state.value = LoanUiState(rows = _state.value.rows, error = traduzirErro(it)) }
    }
    fun devolver(id: Long, uid: String, gestao: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null, notice = null)
        runCatching { repository.devolver(id); if (gestao) repository.listarGestao() else repository.historico(uid) }
            .onSuccess { _state.value = LoanUiState(rows = it, notice = "Devolução registrada.") }
            .onFailure { _state.value = LoanUiState(rows = _state.value.rows, error = traduzirErro(it)) }
    }
}