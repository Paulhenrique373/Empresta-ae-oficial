package com.example.emprestaa.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Dados digitados no formulário de equipamento (cadastro e edição). */
data class FormularioEquipamento(
    val marca: String,
    val modelo: String,
    val etiqueta: String,
    val tipo: String,
    val usoTecnico: Boolean,
    val status: String,
    val observacao: String
)

data class GestaoEquipamentosUiState(
    val carregando: Boolean = true,
    val atualizando: Boolean = false,
    val itens: List<EquipamentoRow> = emptyList(),
    val erro: String? = null,
    val salvando: Boolean = false,
    /** Erro do formulário: aparece dentro da janela de cadastro/edição para o usuário corrigir. */
    val erroSalvar: String? = null,
    /** Mensagem de sucesso a ser mostrada uma vez (ex.: "Equipamento cadastrado."). */
    val aviso: String? = null
)

private fun mensagemSalvar(erro: Throwable, novo: Boolean): String {
    val texto = (erro.message ?: "").lowercase()
    return when {
        "duplicate key" in texto || "already exists" in texto ->
            if (novo) "Já existe um equipamento com esta etiqueta." else "Já existe um equipamento com estes dados."
        "row-level security" in texto || "permission denied" in texto ->
            "Sua conta não tem permissão para fazer esta alteração."
        else -> traduzirErro(erro)
    }
}

/**
 * Gestão de equipamentos. Reaproveita o EquipamentoRepository existente (listar, cadastrar, alterar).
 * Antes de gravar, confirma no servidor (usuario_e_gestao) que a conta é da Gestão; a RLS continua valendo.
 */
class GestaoEquipamentosViewModel(
    private val repository: EquipamentoRepository = EquipamentoRepository(),
    private val gestao: GestaoRepository = GestaoRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(GestaoEquipamentosUiState())
    val state = _state.asStateFlow()
    private var trabalho: Job? = null

    /** silencioso = true é a atualização automática: não mostra carregamento nem erro. */
    fun carregar(silencioso: Boolean = false) {
        if (silencioso && (trabalho?.isActive == true || _state.value.salvando)) return
        if (!silencioso) trabalho?.cancel()
        trabalho = viewModelScope.launch {
            if (!silencioso) {
                val atual = _state.value
                _state.value = atual.copy(carregando = atual.itens.isEmpty(), atualizando = atual.itens.isNotEmpty(), erro = null)
            }
            runCatching { repository.listar() }
                .onSuccess { _state.value = _state.value.copy(carregando = false, atualizando = false, itens = it, erro = null) }
                .onFailure { erro ->
                    if (erro is CancellationException) throw erro
                    if (!silencioso) _state.value = _state.value.copy(carregando = false, atualizando = false, erro = traduzirErro(erro))
                }
        }
    }

    /** Cadastra (editando == null) ou altera o equipamento informado. Chama onSucesso quando terminar. */
    fun salvar(editando: EquipamentoRow?, form: FormularioEquipamento, onSucesso: () -> Unit) {
        if (_state.value.salvando) return
        viewModelScope.launch {
            _state.value = _state.value.copy(salvando = true, erroSalvar = null)
            runCatching {
                check(gestao.usuarioEGestao()) { "Esta conta não tem permissão de Gestão." }
                val observacao = form.observacao.trim()
                if (editando == null) {
                    repository.cadastrar(
                        EquipamentoInsert(
                            marca = form.marca.trim(),
                            modelo = form.modelo.trim(),
                            etiqueta = form.etiqueta.trim(),
                            tipo = form.tipo.trim(),
                            usoTecnico = form.usoTecnico,
                            observacao = observacao.ifBlank { null }
                        )
                    )
                } else {
                    repository.alterar(
                        editando.id,
                        EquipamentoPatch(
                            marca = form.marca.trim(),
                            modelo = form.modelo.trim(),
                            tipo = form.tipo.trim(),
                            usoTecnico = form.usoTecnico,
                            status = form.status,
                            // Campo vazio só é enviado como "" quando havia texto antes (para limpar a observação).
                            observacao = if (observacao.isNotBlank()) observacao else if (editando.observacao.isNullOrBlank()) null else ""
                        )
                    )
                }
                repository.listar()
            }.onSuccess { lista ->
                _state.value = _state.value.copy(
                    itens = lista,
                    salvando = false,
                    erro = null,
                    aviso = if (editando == null) "Equipamento cadastrado." else "Equipamento atualizado."
                )
                onSucesso()
            }.onFailure { erro ->
                if (erro is CancellationException) throw erro
                _state.value = _state.value.copy(salvando = false, erroSalvar = mensagemSalvar(erro, editando == null))
            }
        }
    }

    fun limparErroSalvar() { _state.value = _state.value.copy(erroSalvar = null) }
    fun consumirAviso() { _state.value = _state.value.copy(aviso = null) }
}