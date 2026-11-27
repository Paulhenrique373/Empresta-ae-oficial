package com.example.emprestaa.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Uma fatia/barra de gráfico. "chave" é o texto normalizado (sem acento, minúsculo) usado para escolher a cor. */
data class FatiaGrafico(val rotulo: String, val valor: Int, val chave: String)

/** Números do painel, calculados a partir dos dados reais do Supabase. */
data class ResumoGestao(
    val totalEquipamentos: Int,
    val notebooks: Int,
    val tablets: Int,
    val disponiveis: Int,
    val emprestados: Int,
    val manutencao: Int,
    val emprestimosAtivos: Int,
    val totalEmprestimos: Int,
    /** null quando a conta não conseguiu ler a lista de usuários. */
    val professores: Int?,
    val defeitosRegistrados: Int,
    val defeitosEmAberto: Int,
    val situacaoEquipamentos: List<FatiaGrafico>,
    val equipamentosPorTipo: List<FatiaGrafico>,
    val defeitosPorSituacao: List<FatiaGrafico>
) {
    val vazio: Boolean get() = totalEquipamentos == 0 && totalEmprestimos == 0 && defeitosRegistrados == 0
}

data class GestaoUiState(
    /** Primeira carga (ainda sem dados na tela). */
    val carregando: Boolean = true,
    /** Atualização manual com dados já na tela. */
    val atualizando: Boolean = false,
    /** O servidor respondeu que esta conta não é da Gestão. */
    val acessoNegado: Boolean = false,
    val erro: String? = null,
    val resumo: ResumoGestao? = null,
    val atualizadoEm: String? = null
)

private val MARCAS_COMBINANTES = Regex("\\p{Mn}+")

/** Texto sem acento, sem espaços nas pontas e em minúsculas (ex.: "Manutenção " vira "manutencao"). */
internal fun normalizarTexto(valor: String): String =
    Normalizer.normalize(valor.trim(), Normalizer.Form.NFD).replace(MARCAS_COMBINANTES, "").lowercase()

private val PERFIS_PROFESSOR = setOf(
    normalizarTexto(PerfilAcesso.PROFESSOR_FGB.label),
    normalizarTexto(PerfilAcesso.PROFESSOR_TECNICO.label)
)

// ATENÇÃO: valores vistos no banco: "Registrado", "Em manutenção" e "Consertado" (este último é encerrado). Um defeito é considerado ENCERRADO se a situação contém uma destas palavras; todo o resto
// conta como "em aberto". Se os valores reais forem outros, ajuste somente esta lista.
private val PALAVRAS_DEFEITO_ENCERRADO = listOf("resolv", "conclu", "fechad", "finaliz", "corrig", "solucion", "conserta")

internal fun defeitoEmAberto(situacao: String): Boolean {
    val s = normalizarTexto(situacao)
    return PALAVRAS_DEFEITO_ENCERRADO.none { it in s }
}

private fun agruparPorTexto(valores: List<String>, semValor: String): List<FatiaGrafico> =
    valores
        .map { it.trim() }
        .groupBy { normalizarTexto(it) }
        .map { (chave, itens) ->
            val rotulo = itens.first().ifBlank { semValor }.replaceFirstChar { it.uppercase() }
            FatiaGrafico(rotulo, itens.size, chave)
        }
        .sortedWith(compareByDescending<FatiaGrafico> { it.valor }.thenBy { it.rotulo })

/** Transforma os dados brutos do Supabase nos números do painel. Função pura (sem rede). */
internal fun calcularResumo(dados: DadosGestao): ResumoGestao {
    val equipamentos = dados.equipamentos
    fun contar(situacao: String) = equipamentos.count { normalizarTexto(it.status.orEmpty()) == situacao }
    val disponiveis = contar("disponivel")
    val emprestados = contar("em uso")
    val manutencao = contar("manutencao")
    val outros = equipamentos.size - disponiveis - emprestados - manutencao

    val situacao = buildList {
        add(FatiaGrafico("Disponíveis", disponiveis, "disponivel"))
        add(FatiaGrafico("Emprestados", emprestados, "em uso"))
        add(FatiaGrafico("Em manutenção", manutencao, "manutencao"))
        if (outros > 0) add(FatiaGrafico("Outras situações", outros, "outros"))
    }

    val professores = dados.professores?.count { u ->
        u.ativo && u.perfis.any { normalizarTexto(it.perfil.nome) in PERFIS_PROFESSOR }
    }

    val situacoesDefeito = dados.defeitos.map { it.status.orEmpty() }
    return ResumoGestao(
        totalEquipamentos = equipamentos.size,
        notebooks = equipamentos.count { it.tipo.orEmpty().contains("notebook", ignoreCase = true) },
        tablets = equipamentos.count { it.tipo.orEmpty().contains("tablet", ignoreCase = true) },
        disponiveis = disponiveis,
        emprestados = emprestados,
        manutencao = manutencao,
        emprestimosAtivos = dados.emprestimos.count { normalizarTexto(it.status.orEmpty()) == "ativo" },
        totalEmprestimos = dados.emprestimos.size,
        professores = professores,
        defeitosRegistrados = dados.defeitos.size,
        defeitosEmAberto = situacoesDefeito.count { defeitoEmAberto(it) },
        situacaoEquipamentos = situacao,
        equipamentosPorTipo = agruparPorTexto(equipamentos.map { it.tipo.orEmpty() }, "Sem tipo"),
        defeitosPorSituacao = agruparPorTexto(situacoesDefeito, "Sem situação")
    )
}

private fun mensagemGestao(erro: Throwable): String {
    val texto = (erro.message ?: "").lowercase()
    return if ("usuario_e_gestao" in texto) "Não foi possível confirmar o acesso da Gestão no servidor. Avise o suporte técnico."
    else traduzirErro(erro)
}

class GestaoViewModel(private val repository: GestaoRepository = GestaoRepository()) : ViewModel() {
    private val _state = MutableStateFlow(GestaoUiState())
    val state = _state.asStateFlow()
    private var trabalho: Job? = null

    /**
     * Valida no servidor (usuario_e_gestao) e carrega os dados. silencioso = true é usado pela atualização
     * automática: não mostra carregamento nem erro, e mantém os números anteriores se falhar.
     */
    fun carregar(silencioso: Boolean = false) {
        if (silencioso && (trabalho?.isActive == true || _state.value.acessoNegado)) return
        if (!silencioso) trabalho?.cancel()
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
                if (repository.usuarioEGestao()) calcularResumo(repository.carregarDados()) else null
            }.onSuccess { resumo ->
                _state.value = if (resumo == null) GestaoUiState(carregando = false, acessoNegado = true)
                else GestaoUiState(carregando = false, resumo = resumo, atualizadoEm = horaAtual())
            }.onFailure { erro ->
                if (erro is CancellationException) throw erro
                if (!silencioso) _state.value = _state.value.copy(carregando = false, atualizando = false, erro = mensagemGestao(erro))
            }
        }
    }

    private fun horaAtual(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
}