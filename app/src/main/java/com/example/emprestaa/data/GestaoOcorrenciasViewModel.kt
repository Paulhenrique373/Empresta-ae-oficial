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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Situações que o app já usa para defeitos (vistas no banco):
 * "Registrado", "Em manutenção" e "Consertado".
 * Nenhuma situação nova foi criada.
 */
val SITUACOES_OCORRENCIA: List<String> =
    listOf("Registrado", "Em manutenção", "Consertado")

/**
 * Agrupamento das situações reais para os indicadores e filtros:
 * PENDENTE = Registrado, EM_ANALISE = Em manutenção, RESOLVIDA = Consertado.
 */
enum class CategoriaOcorrencia {
    PENDENTE,
    EM_ANALISE,
    RESOLVIDA
}

enum class FiltroOcorrencia(val rotulo: String) {
    TODAS("Todas"),
    PENDENTES("Pendentes"),
    EM_ANALISE("Em análise"),
    RESOLVIDAS("Resolvidas")
}

/**
 * Uma ocorrência (defeito) já combinada com equipamento e professor.
 * Campos que o banco não devolveu ficam null.
 */
data class OcorrenciaGestao(
    val id: Long,
    val equipamentoId: Long?,
    val usuarioId: String?,
    val professor: String?,
    val equipamento: String?,
    val marca: String?,
    val modelo: String?,
    val etiqueta: String?,
    val tipoEquipamento: String?,
    val tipoDefeito: String?,
    val descricao: String?,
    val dataTexto: String?,
    val dataMs: Long?,
    val situacao: String,
    val categoria: CategoriaOcorrencia,
    val textoBusca: String
)

data class ResumoOcorrencias(
    val total: Int,
    val pendentes: Int,
    val emAnalise: Int,
    val resolvidas: Int,
    val equipamentosComProblema: Int
)

data class GestaoOcorrenciasUiState(
    val carregando: Boolean = true,
    val atualizando: Boolean = false,
    val acessoNegado: Boolean = false,
    val erro: String? = null,
    val itens: List<OcorrenciaGestao> = emptyList(),
    val resumo: ResumoOcorrencias? = null,
    val professoresIndisponiveis: Boolean = false,
    val equipamentosIndisponiveis: Boolean = false,
    val atualizadoEm: String? = null,

    /** true enquanto a alteração de situação está sendo gravada. */
    val salvando: Boolean = false,

    /** Erro da alteração de situação (aparece dentro da janela de detalhes). */
    val erroStatus: String? = null,

    /** Mensagem de sucesso mostrada uma vez. */
    val aviso: String? = null
)

/**
 * Classifica a situação real do banco.
 * Reaproveita defeitoEmAberto() do painel da Gestão, para os números
 * do painel e deste módulo sempre concordarem.
 */
internal fun categoriaDaSituacao(situacao: String): CategoriaOcorrencia {
    val s = normalizarTexto(situacao)

    return when {
        !defeitoEmAberto(situacao) -> CategoriaOcorrencia.RESOLVIDA
        "manuten" in s || "analise" in s -> CategoriaOcorrencia.EM_ANALISE
        else -> CategoriaOcorrencia.PENDENTE
    }
}

/**
 * Junta defeitos, equipamentos e usuários.
 * Função pura, sem acesso à rede.
 */
internal fun montarOcorrencias(
    defeitos: List<DefeitoGestaoRow>,
    equipamentos: List<EquipamentoRow>?,
    usuarios: List<UsuarioAdminRow>?
): List<OcorrenciaGestao> {

    val equipamentoPorId =
        equipamentos?.associateBy { it.id }.orEmpty()

    val nomePorId =
        usuarios?.associate { it.id to it.nome.trim() }.orEmpty()

    return defeitos.map { linha ->

        val eq = linha.equipamentoId?.let { equipamentoPorId[it] }

        val marca = eq?.marca?.trim()?.ifBlank { null }
        val modelo = eq?.modelo?.trim()?.ifBlank { null }
        val etiqueta = eq?.etiqueta?.trim()?.ifBlank { null }
        val tipoEquipamento = eq?.tipo?.trim()?.ifBlank { null }

        val nomeEquipamento =
            if (eq == null) {
                null
            } else {
                listOfNotNull(marca, modelo)
                    .joinToString(" ")
                    .ifBlank { null }
            }

        val professor = linha.usuarioId
            ?.let { nomePorId[it] }
            ?.ifBlank { null }

        val tipoDefeito = linha.tipo?.trim()?.ifBlank { null }
        val descricao = linha.descricao?.trim()?.ifBlank { null }

        val situacao = linha.status
            ?.trim()
            .orEmpty()
            .ifBlank { "Sem situação" }

        OcorrenciaGestao(
            id = linha.id,
            equipamentoId = linha.equipamentoId,
            usuarioId = linha.usuarioId,
            professor = professor,
            equipamento = nomeEquipamento,
            marca = marca,
            modelo = modelo,
            etiqueta = etiqueta,
            tipoEquipamento = tipoEquipamento,
            tipoDefeito = tipoDefeito,
            descricao = descricao,
            dataTexto = formatarDataHora(linha.data),
            dataMs = instanteEmMs(linha.data),
            situacao = situacao,
            categoria = categoriaDaSituacao(situacao),
            textoBusca = normalizarTexto(
                listOfNotNull(
                    nomeEquipamento,
                    etiqueta,
                    professor,
                    tipoDefeito
                ).joinToString(" ")
            )
        )
    }.sortedWith(
        compareByDescending<OcorrenciaGestao> {
            it.dataMs ?: Long.MIN_VALUE
        }.thenByDescending {
            it.id
        }
    )
}

/**
 * Indicadores. Cada defeito é uma linha única (id_defeito),
 * e cada equipamento é contado uma única vez.
 */
internal fun calcularResumoOcorrencias(
    itens: List<OcorrenciaGestao>
): ResumoOcorrencias =
    ResumoOcorrencias(
        total = itens.size,
        pendentes = itens.count {
            it.categoria == CategoriaOcorrencia.PENDENTE
        },
        emAnalise = itens.count {
            it.categoria == CategoriaOcorrencia.EM_ANALISE
        },
        resolvidas = itens.count {
            it.categoria == CategoriaOcorrencia.RESOLVIDA
        },
        equipamentosComProblema = itens
            .mapNotNull { it.equipamentoId }
            .distinct()
            .size
    )

/**
 * Pesquisa e filtro funcionando em conjunto.
 * A pesquisa ignora acentos e maiúsculas.
 */
internal fun filtrarOcorrencias(
    itens: List<OcorrenciaGestao>,
    consulta: String,
    filtro: FiltroOcorrencia
): List<OcorrenciaGestao> {

    val palavras = normalizarTexto(consulta)
        .split(' ')
        .filter { it.isNotBlank() }

    return itens.filter { item ->

        val situacaoOk = when (filtro) {
            FiltroOcorrencia.TODAS -> true
            FiltroOcorrencia.PENDENTES ->
                item.categoria == CategoriaOcorrencia.PENDENTE
            FiltroOcorrencia.EM_ANALISE ->
                item.categoria == CategoriaOcorrencia.EM_ANALISE
            FiltroOcorrencia.RESOLVIDAS ->
                item.categoria == CategoriaOcorrencia.RESOLVIDA
        }

        situacaoOk && palavras.all { it in item.textoBusca }
    }
}

private fun mensagemOcorrencias(erro: Throwable): String {
    val texto = (erro.message ?: "").lowercase()

    return if ("usuario_e_gestao" in texto) {
        "Não foi possível confirmar o acesso da Gestão no servidor. Avise o suporte técnico."
    } else {
        traduzirErro(erro)
    }
}

private fun mensagemAlteracao(erro: Throwable): String {
    val texto = (erro.message ?: "").lowercase()

    return when {
        "usuario_e_gestao" in texto ->
            "Não foi possível confirmar o acesso da Gestão no servidor. Avise o suporte técnico."

        "row-level security" in texto || "permission denied" in texto ->
            "Sua conta não tem permissão para alterar esta ocorrência."

        "check constraint" in texto || "violates check" in texto ->
            "O banco de dados não aceitou esta situação para a ocorrência."

        else -> traduzirErro(erro)
    }
}

/** Resultado de uma leitura completa. */
internal class LeituraOcorrencias(
    val itens: List<OcorrenciaGestao>,
    val resumo: ResumoOcorrencias,
    val professoresIndisponiveis: Boolean,
    val equipamentosIndisponiveis: Boolean
)

/**
 * Ocorrências e defeitos para a Gestão.
 *
 * Reaproveita DefeitoRepository (listagem e atualizarStatus),
 * EquipamentoRepository e UsuarioRepository.
 * O perfil é validado no servidor (usuario_e_gestao) e a RLS do
 * Supabase continua decidindo o que a conta consegue ler e alterar.
 */
class GestaoOcorrenciasViewModel(
    private val defeitos: DefeitoRepository = DefeitoRepository(),
    private val equipamentos: EquipamentoRepository = EquipamentoRepository(),
    private val usuarios: UsuarioRepository = UsuarioRepository(),
    private val gestao: GestaoRepository = GestaoRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(GestaoOcorrenciasUiState())
    val state = _state.asStateFlow()

    private var trabalho: Job? = null
    private var acessoConfirmado = false

    /**
     * silencioso = true é a atualização automática.
     * Não mostra carregamento nem erro e mantém os dados anteriores
     * se a leitura falhar ou vier incompleta.
     */
    fun carregar(silencioso: Boolean = false) {

        // Evita sobrescrever uma alteração de situação em andamento.
        if (_state.value.salvando) return

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

            }.onSuccess { leitura ->

                if (leitura == null) {
                    _state.value = GestaoOcorrenciasUiState(
                        carregando = false,
                        acessoNegado = true
                    )
                } else {

                    val incompleto =
                        leitura.professoresIndisponiveis ||
                                leitura.equipamentosIndisponiveis

                    // Mantém os dados anteriores se a atualização
                    // automática vier incompleta.
                    if (
                        silencioso &&
                        incompleto &&
                        _state.value.resumo != null
                    ) {
                        return@onSuccess
                    }

                    _state.value = aplicar(leitura)
                }

            }.onFailure { erro ->

                if (erro is CancellationException) {
                    throw erro
                }

                if (!silencioso) {
                    _state.value = _state.value.copy(
                        carregando = false,
                        atualizando = false,
                        erro = mensagemOcorrencias(erro)
                    )
                }
            }
        }
    }

    /**
     * Altera a situação de uma ocorrência usando o método existente
     * DefeitoRepository.atualizarStatus.
     *
     * O Supabase pode ignorar silenciosamente uma gravação bloqueada
     * pela RLS (0 linhas alteradas, sem erro). Por isso a lista é lida
     * de novo e a nova situação é conferida antes de mostrar sucesso.
     */
    fun alterarSituacao(id: Long, novaSituacao: String) {

        if (_state.value.salvando) return

        if (novaSituacao !in SITUACOES_OCORRENCIA) {
            _state.value = _state.value.copy(
                erroStatus = "Situação não permitida."
            )
            return
        }

        // Uma leitura antiga não pode sobrescrever o resultado.
        trabalho?.cancel()

        _state.value = _state.value.copy(
            carregando = false,
            atualizando = false,
            salvando = true,
            erroStatus = null
        )

        viewModelScope.launch {

            runCatching {

                check(gestao.usuarioEGestao()) {
                    "Esta conta não tem permissão de Gestão."
                }

                defeitos.atualizarStatus(id, novaSituacao)

                val leitura = lerTudo()

                val gravada = leitura.itens.firstOrNull { it.id == id }

                check(
                    gravada != null &&
                            normalizarTexto(gravada.situacao) ==
                            normalizarTexto(novaSituacao)
                ) {
                    "A alteração não foi aplicada. Sua conta pode não ter permissão para alterar ocorrências."
                }

                leitura

            }.onSuccess { leitura ->

                acessoConfirmado = true

                _state.value = aplicar(leitura).copy(
                    salvando = false,
                    erroStatus = null,
                    aviso = "Ocorrência nº $id alterada para $novaSituacao."
                )

            }.onFailure { erro ->

                if (erro is CancellationException) {
                    throw erro
                }

                _state.value = _state.value.copy(
                    salvando = false,
                    erroStatus = mensagemAlteracao(erro)
                )
            }
        }
    }

    fun limparErroStatus() {
        _state.value = _state.value.copy(erroStatus = null)
    }

    fun consumirAviso() {
        _state.value = _state.value.copy(aviso = null)
    }

    private fun aplicar(
        leitura: LeituraOcorrencias
    ): GestaoOcorrenciasUiState =
        _state.value.copy(
            carregando = false,
            atualizando = false,
            acessoNegado = false,
            erro = null,
            itens = leitura.itens,
            resumo = leitura.resumo,
            professoresIndisponiveis = leitura.professoresIndisponiveis,
            equipamentosIndisponiveis = leitura.equipamentosIndisponiveis,
            atualizadoEm = LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        )

    /**
     * Lê as três tabelas em paralelo.
     * Só a lista de defeitos é obrigatória.
     */
    private suspend fun lerTudo(): LeituraOcorrencias =
        coroutineScope {

            val listaDefeitos = async {
                defeitos.listarGestao()
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

            val linhas = listaDefeitos.await()
            val eq = listaEquipamentos.await()
            val us = listaUsuarios.await()

            val itens = montarOcorrencias(
                defeitos = linhas,
                equipamentos = eq,
                usuarios = us
            )

            LeituraOcorrencias(
                itens = itens,
                resumo = calcularResumoOcorrencias(itens),
                professoresIndisponiveis = us == null,
                equipamentosIndisponiveis = eq == null
            )
        }
}