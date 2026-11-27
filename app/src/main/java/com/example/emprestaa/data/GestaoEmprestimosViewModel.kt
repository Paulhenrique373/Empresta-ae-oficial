
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Filtro por situação da lista geral de empréstimos.
 */
enum class FiltroEmprestimo(val rotulo: String) {
    TODOS("Todos"),
    ATIVOS("Ativos"),
    DEVOLVIDOS("Devolvidos")
}

/**
 * Um empréstimo já combinado com o professor e o equipamento.
 * Campos que o banco não devolveu ficam null.
 */
data class EmprestimoGestao(
    val id: Long,

    // CORREÇÃO: o usuário pode não estar informado no banco.
    val usuarioId: String?,

    val equipamentoId: Long,
    val professor: String?,
    val equipamento: String?,
    val marca: String?,
    val modelo: String?,
    val etiqueta: String?,
    val tipo: String?,
    val retiradaTexto: String?,
    val devolucaoTexto: String?,
    val retiradaMs: Long?,
    val devolucaoMs: Long?,
    val situacao: String,
    val ativo: Boolean,
    val devolvido: Boolean,
    val textoBusca: String
)

/**
 * Indicadores do módulo.
 */
data class ResumoEmprestimos(
    val total: Int,
    val ativos: Int,
    val devolvidos: Int,
    val equipamentosEmprestados: Int,
    val professoresComEmprestimo: Int
)

data class GestaoEmprestimosUiState(
    val carregando: Boolean = true,
    val atualizando: Boolean = false,
    val acessoNegado: Boolean = false,
    val erro: String? = null,
    val itens: List<EmprestimoGestao> = emptyList(),
    val resumo: ResumoEmprestimos? = null,
    val professoresIndisponiveis: Boolean = false,
    val equipamentosIndisponiveis: Boolean = false,
    val atualizadoEm: String? = null
)

private val FORMATO_DATA_HORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/**
 * Converte o texto de data do Supabase em milissegundos.
 * Aceita com ou sem fuso.
 */
internal fun instanteEmMs(iso: String?): Long? {
    val texto = iso
        ?.trim()
        ?.replace(' ', 'T')
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    return runCatching {
        OffsetDateTime.parse(texto).toInstant().toEpochMilli()
    }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(texto)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
}

/**
 * Formata a data para dd/MM/yyyy HH:mm.
 */
internal fun formatarDataHora(iso: String?): String? {
    val texto = iso?.trim().orEmpty()

    if (texto.isEmpty()) return null

    val ms = instanteEmMs(texto) ?: return texto

    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .format(FORMATO_DATA_HORA)
}

/**
 * Junta empréstimos, equipamentos e usuários.
 * Função pura, sem acesso à rede.
 */
internal fun montarEmprestimos(
    emprestimos: List<EmprestimoRow>,
    equipamentos: List<EquipamentoRow>?,
    usuarios: List<UsuarioAdminRow>?
): List<EmprestimoGestao> {

    val equipamentoPorId =
        equipamentos?.associateBy { it.id }.orEmpty()

    val nomePorId =
        usuarios?.associate { it.id to it.nome.trim() }.orEmpty()

    return emprestimos.map { linha ->

        val eq = equipamentoPorId[linha.equipamentoId]

        val marca = eq?.marca
            ?.trim()
            ?.ifBlank { null }

        val modelo = eq?.modelo
            ?.trim()
            ?.ifBlank { null }

        val etiqueta = eq?.etiqueta
            ?.trim()
            ?.ifBlank { null }

        val tipo = eq?.tipo
            ?.trim()
            ?.ifBlank { null }

        val nomeEquipamento =
            if (eq == null) {
                null
            } else {
                listOfNotNull(marca, modelo)
                    .joinToString(" ")
                    .ifBlank { null }
            }

        // CORREÇÃO: trata usuarioId nulo antes de procurar o professor.
        val professor = linha.usuarioId
            ?.let { nomePorId[it] }
            ?.ifBlank { null }

        val situacaoNormalizada =
            normalizarTexto(linha.status)

        EmprestimoGestao(
            id = linha.id,
            usuarioId = linha.usuarioId,
            equipamentoId = linha.equipamentoId,
            professor = professor,
            equipamento = nomeEquipamento,
            marca = marca,
            modelo = modelo,
            etiqueta = etiqueta,
            tipo = tipo,
            retiradaTexto = formatarDataHora(linha.retirada),
            devolucaoTexto = formatarDataHora(linha.devolucao),
            retiradaMs = instanteEmMs(linha.retirada),
            devolucaoMs = instanteEmMs(linha.devolucao),
            situacao = linha.status.trim()
                .ifBlank { "Sem situação" },
            ativo = situacaoNormalizada == "ativo",
            devolvido = situacaoNormalizada == "devolvido",
            textoBusca = normalizarTexto(
                listOfNotNull(
                    professor,
                    etiqueta,
                    nomeEquipamento,
                    marca,
                    modelo,
                    tipo
                ).joinToString(" ")
            )
        )
    }.sortedWith(
        compareByDescending<EmprestimoGestao> {
            it.retiradaMs ?: Long.MIN_VALUE
        }.thenByDescending {
            it.id
        }
    )
}

/**
 * Indicadores do módulo.
 * Equipamentos e professores são contados uma única vez.
 */
internal fun calcularResumoEmprestimos(
    itens: List<EmprestimoGestao>
): ResumoEmprestimos {

    val ativos = itens.filter { it.ativo }

    return ResumoEmprestimos(
        total = itens.size,
        ativos = ativos.size,
        devolvidos = itens.count { it.devolvido },

        equipamentosEmprestados = ativos
            .map { it.equipamentoId }
            .distinct()
            .size,

        // CORREÇÃO: ignora usuários nulos na contagem.
        professoresComEmprestimo = ativos
            .mapNotNull { it.usuarioId }
            .distinct()
            .size
    )
}

/**
 * Pesquisa e filtro funcionando em conjunto.
 * A pesquisa ignora acentos e maiúsculas.
 */
internal fun filtrarEmprestimos(
    itens: List<EmprestimoGestao>,
    consulta: String,
    filtro: FiltroEmprestimo
): List<EmprestimoGestao> {

    val palavras = normalizarTexto(consulta)
        .split(' ')
        .filter { it.isNotBlank() }

    return itens.filter { item ->

        val situacaoOk = when (filtro) {
            FiltroEmprestimo.TODOS -> true
            FiltroEmprestimo.ATIVOS -> item.ativo
            FiltroEmprestimo.DEVOLVIDOS -> item.devolvido
        }

        situacaoOk && palavras.all {
            it in item.textoBusca
        }
    }
}

/**
 * Somente os já devolvidos,
 * da devolução mais recente para a mais antiga.
 */
internal fun historicoDevolucoes(
    itens: List<EmprestimoGestao>
): List<EmprestimoGestao> =
    itens.filter { it.devolvido }
        .sortedWith(
            compareByDescending<EmprestimoGestao> {
                it.devolucaoMs ?: it.retiradaMs ?: Long.MIN_VALUE
            }.thenByDescending {
                it.retiradaMs ?: Long.MIN_VALUE
            }.thenByDescending {
                it.id
            }
        )

private fun mensagemEmprestimos(erro: Throwable): String {
    val texto = (erro.message ?: "").lowercase()

    return if ("usuario_e_gestao" in texto) {
        "Não foi possível confirmar o acesso da Gestão no servidor. Avise o suporte técnico."
    } else {
        traduzirErro(erro)
    }
}

/**
 * Empréstimos e devoluções para a Gestão.
 * Somente consulta.
 *
 * O perfil é validado no servidor e a RLS do Supabase
 * continua decidindo o que a conta consegue ler.
 */
class GestaoEmprestimosViewModel(
    private val emprestimos: EmprestimoRepository = EmprestimoRepository(),
    private val equipamentos: EquipamentoRepository = EquipamentoRepository(),
    private val usuarios: UsuarioRepository = UsuarioRepository(),
    private val gestao: GestaoRepository = GestaoRepository()
) : ViewModel() {

    private val _state =
        MutableStateFlow(GestaoEmprestimosUiState())

    val state = _state.asStateFlow()

    private var trabalho: Job? = null
    private var acessoConfirmado = false

    /**
     * silencioso = true é a atualização automática.
     * Mantém os dados anteriores se a leitura falhar ou vier incompleta.
     */
    fun carregar(silencioso: Boolean = false) {

        if (
            silencioso &&
            (trabalho?.isActive == true || _state.value.acessoNegado)
        ) return

        if (!silencioso) {
            trabalho?.cancel()
        }

        trabalho = viewModelScope.launch {

            if (!silencioso) {
                val atual = _state.value

                _state.value = atual.copy(
                    carregando = atual.resumo == null,
                    atualizando = atual.resumo != null,
                    erro = null,
                    acessoNegado = false
                )
            }

            runCatching {

                if (!silencioso || !acessoConfirmado) {
                    acessoConfirmado = gestao.usuarioEGestao()
                }

                if (acessoConfirmado) {
                    lerTudo()
                } else {
                    null
                }

            }.onSuccess { novo ->

                if (novo == null) {
                    _state.value = GestaoEmprestimosUiState(
                        carregando = false,
                        acessoNegado = true
                    )
                } else {

                    val incompleto =
                        novo.professoresIndisponiveis ||
                                novo.equipamentosIndisponiveis

                    // Mantém os dados anteriores se a atualização
                    // automática vier incompleta.
                    if (
                        silencioso &&
                        incompleto &&
                        _state.value.resumo != null
                    ) {
                        return@onSuccess
                    }

                    _state.value = novo
                }

            }.onFailure { erro ->

                if (erro is CancellationException) {
                    throw erro
                }

                if (!silencioso) {
                    _state.value = _state.value.copy(
                        carregando = false,
                        atualizando = false,
                        erro = mensagemEmprestimos(erro)
                    )
                }
            }
        }
    }

    /**
     * Lê as três tabelas em paralelo.
     * Só a lista de empréstimos é obrigatória.
     */
    private suspend fun lerTudo(): GestaoEmprestimosUiState =
        coroutineScope {

            val listaEmprestimos = async {
                emprestimos.listarGestao()
            }

            val listaEquipamentos = async {
                try {
                    equipamentos.listar()
                } catch (erro: CancellationException) {
                    throw erro
                } catch (erro: Throwable) {
                    null
                }
            }

            val listaUsuarios = async {
                try {
                    usuarios.listar()
                } catch (erro: CancellationException) {
                    throw erro
                } catch (erro: Throwable) {
                    null
                }
            }

            val linhas = listaEmprestimos.await()
            val eq = listaEquipamentos.await()
            val us = listaUsuarios.await()

            val itens = montarEmprestimos(
                emprestimos = linhas,
                equipamentos = eq,
                usuarios = us
            )

            GestaoEmprestimosUiState(
                carregando = false,
                atualizando = false,
                acessoNegado = false,
                erro = null,
                itens = itens,
                resumo = calcularResumoEmprestimos(itens),
                professoresIndisponiveis = us == null,
                equipamentosIndisponiveis = eq == null,
                atualizadoEm = LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            )
        }
}
