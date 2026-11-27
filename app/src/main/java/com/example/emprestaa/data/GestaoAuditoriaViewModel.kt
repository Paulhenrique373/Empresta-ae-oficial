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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Categoria visual de um registro de auditoria.
 * Cada registro pertence a UMA só categoria (assim os totais nunca contam o mesmo registro duas vezes).
 */
enum class CategoriaAuditoria(val rotulo: String) {
    EMPRESTIMO("Empréstimo"),
    DEVOLUCAO("Devolução"),
    EQUIPAMENTO("Equipamento"),
    OCORRENCIA("Ocorrência"),
    ADMINISTRATIVA("Alteração administrativa"),
    OUTRAS("Outras atividades")
}

/**
 * Um registro da tabela de auditoria já combinado com nomes de usuário e equipamento.
 * Tudo que o banco não informou fica null; nada é inventado.
 */
data class RegistroAuditoria(
    val chave: String,
    /** Texto curto da atividade, montado só com a operação (INSERT/UPDATE/DELETE) e a tabela reais. */
    val titulo: String,
    val id: String?,
    val usuarioId: String?,
    val usuario: String?,
    val acao: String?,
    val tabela: String?,
    val registroId: String?,
    val descricao: String?,
    val equipamentoId: Long?,
    val equipamento: String?,
    val emprestimoId: Long?,
    val ocorrenciaId: Long?,
    val dataTexto: String?,
    val dataMs: Long?,
    val categoria: CategoriaAuditoria,
    val anterior: String?,
    val posterior: String?,
    /** Conteúdo da coluna de detalhes (chave, valor), quando existir. */
    val adicionais: List<Pair<String, String>>,
    /** Todas as colunas não vazias exatamente como o banco devolveu. */
    val campos: List<Pair<String, String>>,
    val textoBusca: String,
    internal val idNumerico: Long?,
    internal val ordem: Int
)

data class ResumoAuditoria(
    val total: Int,
    /** Registros dos últimos 7 dias. */
    val recentes: Int,
    val emprestimos: Int,
    val devolucoes: Int,
    val equipamentos: Int,
    val ocorrencias: Int,
    val administrativas: Int,
    val outras: Int
)

data class GestaoAuditoriaUiState(
    /** Primeira carga (ainda sem dados na tela). */
    val carregando: Boolean = true,
    /** Atualização com dados já na tela. */
    val atualizando: Boolean = false,
    val acessoNegado: Boolean = false,
    val erro: String? = null,
    /** null = ainda não houve leitura bem-sucedida. */
    val registros: List<RegistroAuditoria>? = null,
    val resumo: ResumoAuditoria? = null,
    val usuariosIndisponiveis: Boolean = false,
    val equipamentosIndisponiveis: Boolean = false,
    /** true quando a lista veio com o limite padrão de 1000 linhas do servidor (pode haver mais antigas). */
    val possivelLimite: Boolean = false,
    val atualizadoEm: String? = null
)

/* ------------------------------------------------------------------ */
/* Leitura tolerante das colunas                                        */
/* ------------------------------------------------------------------ */

/*
 * Colunas reais da tabela "auditoria" (confirmadas no Supabase em 27/11/2026):
 * id_auditoria (bigint), id_usuario (uuid, anulável), acao (INSERT/UPDATE/DELETE), tabela_afetada,
 * registro_id (bigint, anulável), data_acao (timestamptz) e detalhes (jsonb, anulável).
 * Dentro de "detalhes" existem "registro" (INSERT) ou "antes"/"depois" (UPDATE), com a linha completa.
 */
private val CHAVES_ID = listOf("id_auditoria")
private val CHAVES_USUARIO = listOf("id_usuario")
private val CHAVES_ACAO = listOf("acao")
private val CHAVES_TABELA = listOf("tabela_afetada")
private val CHAVES_REGISTRO = listOf("registro_id")
private val CHAVES_DATA = listOf("data_acao")
private val CHAVES_DETALHES = listOf("detalhes")
private val CHAVES_EQUIPAMENTO = listOf("id_equipamento")
private val CHAVES_EMPRESTIMO = listOf("id_emprestimo")
private val CHAVES_DEFEITO = listOf("id_defeito")

private val JSON_TOLERANTE = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.primeiro(chaves: List<String>): JsonElement? {
    for (chave in chaves) {
        val valor = this[chave]
        if (valor != null && valor !is JsonNull) return valor
    }
    return null
}

/** Texto legível de qualquer valor JSON. null quando vazio. */
private fun JsonElement.comoTexto(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> content.trim().takeIf { it.isNotEmpty() }
    is JsonObject -> entries
        .mapNotNull { (chave, valor) -> valor.comoTexto()?.let { "$chave: $it" } }
        .joinToString("; ")
        .takeIf { it.isNotEmpty() }
    is JsonArray -> mapNotNull { it.comoTexto() }
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }
}

private fun JsonElement?.comoLong(): Long? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.toLongOrNull()

/** A coluna de detalhes pode vir como objeto JSON ou como texto contendo JSON. */
private fun detalhesComoObjeto(elemento: JsonElement?): JsonObject? = when (elemento) {
    is JsonObject -> elemento
    is JsonPrimitive ->
        if (elemento is JsonNull) null
        else runCatching { JSON_TOLERANTE.parseToJsonElement(elemento.content) as? JsonObject }.getOrNull()
    else -> null
}

/** "status_anterior" -> "Status anterior". */
internal fun nomeLegivel(chave: String): String =
    chave.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

/**
 * A coluna "acao" guarda só a operação do banco (INSERT, UPDATE...). A categoria vem da tabela afetada e,
 * para empréstimos, da mudança real de situação (Ativo -> Devolvido) registrada em "detalhes".
 */
private fun classificar(
    acao: String?,
    tabela: String?,
    situacaoAntes: String?,
    situacaoDepois: String?
): CategoriaAuditoria {
    val t = normalizarTexto(tabela.orEmpty())
    val a = normalizarTexto(acao.orEmpty())
    return when {
        "emprest" in t ->
            if (normalizarTexto(situacaoDepois.orEmpty()) == "devolvido" &&
                normalizarTexto(situacaoAntes.orEmpty()) != "devolvido"
            ) CategoriaAuditoria.DEVOLUCAO
            else CategoriaAuditoria.EMPRESTIMO
        "defeit" in t || "ocorr" in t -> CategoriaAuditoria.OCORRENCIA
        "equip" in t -> CategoriaAuditoria.EQUIPAMENTO
        "usuario" in t || "perfil" in t || "perfis" in t -> CategoriaAuditoria.ADMINISTRATIVA
        "devol" in a -> CategoriaAuditoria.DEVOLUCAO
        "emprest" in a -> CategoriaAuditoria.EMPRESTIMO
        else -> CategoriaAuditoria.OUTRAS
    }
}

private fun montarTitulo(categoria: CategoriaAuditoria, acao: String?, tabela: String?): String {
    val operacao = when (normalizarTexto(acao.orEmpty())) {
        "insert" -> 0
        "update" -> 1
        "delete" -> 2
        else -> null
    }
    fun escolher(criado: String, alterado: String, excluido: String): String? =
        when (operacao) {
            0 -> criado
            1 -> alterado
            2 -> excluido
            else -> null
        }
    val porCategoria = when (categoria) {
        CategoriaAuditoria.EMPRESTIMO -> escolher("Empréstimo registrado", "Empréstimo alterado", "Empréstimo excluído")
        CategoriaAuditoria.DEVOLUCAO -> "Devolução registrada"
        CategoriaAuditoria.EQUIPAMENTO -> escolher("Equipamento cadastrado", "Equipamento alterado", "Equipamento excluído")
        CategoriaAuditoria.OCORRENCIA -> escolher("Ocorrência registrada", "Ocorrência alterada", "Ocorrência excluída")
        CategoriaAuditoria.ADMINISTRATIVA -> escolher(
            "Cadastro administrativo (${tabela.orEmpty()})",
            "Alteração administrativa (${tabela.orEmpty()})",
            "Exclusão administrativa (${tabela.orEmpty()})"
        )
        CategoriaAuditoria.OUTRAS -> null
    }
    return porCategoria
        ?: listOfNotNull(acao?.let { nomeLegivel(it.lowercase()) }, tabela).joinToString(" em ")
            .ifEmpty { categoria.rotulo }
}

/**
 * Junta os registros de auditoria com usuários, equipamentos, empréstimos e defeitos.
 * Função pura (sem rede). Retorna do mais recente para o mais antigo.
 */
internal fun montarRegistrosAuditoria(
    linhas: List<JsonObject>,
    usuarios: List<UsuarioAdminRow>?,
    equipamentos: List<EquipamentoRow>?,
    emprestimos: List<EmprestimoRow>?,
    defeitos: List<DefeitoGestaoRow>?
): List<RegistroAuditoria> {

    val nomePorUsuario = usuarios?.associate { it.id to it.nome.trim() }.orEmpty()
    val equipamentoPorId = equipamentos?.associateBy { it.id }.orEmpty()
    val equipamentoDoEmprestimo = emprestimos?.associate { it.id to it.equipamentoId }.orEmpty()
    val equipamentoDoDefeito = defeitos
        ?.mapNotNull { d -> d.equipamentoId?.let { d.id to it } }
        ?.toMap()
        .orEmpty()

    val chavesUsadas = HashSet<String>()

    val registros = linhas.mapIndexed { ordem, linha ->

        val detalhesBrutos = linha.primeiro(CHAVES_DETALHES)
        val detalhes = detalhesComoObjeto(detalhesBrutos)
        val antes = detalhes?.get("antes") as? JsonObject
        val depois = detalhes?.get("depois") as? JsonObject
        val registroInserido = detalhes?.get("registro") as? JsonObject

        // Procura primeiro na linha, depois no topo de "detalhes" e por fim nas fotos da linha (depois, registro, antes).
        fun procurar(chaves: List<String>): JsonElement? =
            linha.primeiro(chaves)
                ?: detalhes?.primeiro(chaves)
                ?: depois?.primeiro(chaves)
                ?: registroInserido?.primeiro(chaves)
                ?: antes?.primeiro(chaves)

        val id = linha.primeiro(CHAVES_ID)?.comoTexto()
        val usuarioId = linha.primeiro(CHAVES_USUARIO)?.comoTexto()
        val acao = linha.primeiro(CHAVES_ACAO)?.comoTexto()
        val tabela = linha.primeiro(CHAVES_TABELA)?.comoTexto()
        val registroId = linha.primeiro(CHAVES_REGISTRO)?.comoTexto()
        val descricao: String? = null // a tabela não tem coluna de descrição
        val dataTexto = linha.primeiro(CHAVES_DATA)?.comoTexto()
        val registroNumero = registroId?.toLongOrNull()
        val tabelaNormal = normalizarTexto(tabela.orEmpty())

        var emprestimoId = procurar(CHAVES_EMPRESTIMO).comoLong()
        var ocorrenciaId = procurar(CHAVES_DEFEITO).comoLong()
        var equipamentoId = procurar(CHAVES_EQUIPAMENTO).comoLong()

        // O registro_id aponta para a linha da tabela afetada.
        when {
            "emprest" in tabelaNormal -> {
                if (emprestimoId == null) emprestimoId = registroNumero
            }
            "defeit" in tabelaNormal || "ocorr" in tabelaNormal -> {
                if (ocorrenciaId == null) ocorrenciaId = registroNumero
            }
            "equip" in tabelaNormal -> {
                if (equipamentoId == null) equipamentoId = registroNumero
            }
        }

        // Quando o registro não traz o equipamento, usa o relacionamento real do empréstimo ou do defeito.
        if (equipamentoId == null && emprestimoId != null) {
            equipamentoId = equipamentoDoEmprestimo[emprestimoId]
        }
        if (equipamentoId == null && ocorrenciaId != null) {
            equipamentoId = equipamentoDoDefeito[ocorrenciaId]
        }

        val equipamentoRow = equipamentoId?.let { equipamentoPorId[it] }
        val equipamentoTexto = equipamentoId?.let { idEq ->
            equipamentoRow?.let { eq ->
                val nome = listOf(eq.marca.trim(), eq.modelo.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                val etiqueta = eq.etiqueta.trim()
                when {
                    etiqueta.isNotEmpty() && nome.isNotEmpty() -> "$nome (etiqueta $etiqueta)"
                    etiqueta.isNotEmpty() -> "Etiqueta $etiqueta"
                    nome.isNotEmpty() -> nome
                    else -> null
                }
            } ?: "Equipamento (id interno $idEq)"
        }

        // UPDATE: só o que mudou (campo: antes -> depois). INSERT/DELETE: os campos da linha. Sem fotos: o texto de detalhes.
        val adicionais: List<Pair<String, String>> = when {
            antes != null && depois != null -> (antes.keys + depois.keys).toSortedSet().mapNotNull { campo ->
                val a = antes[campo] ?: JsonNull
                val d = depois[campo] ?: JsonNull
                if (a == d) null
                else nomeLegivel(campo) to "${a.comoTexto() ?: "vazio"} → ${d.comoTexto() ?: "vazio"}"
            }
            registroInserido != null -> registroInserido.entries.mapNotNull { (campo, valor) ->
                valor.comoTexto()?.let { nomeLegivel(campo) to it }
            }
            antes != null -> antes.entries.mapNotNull { (campo, valor) ->
                valor.comoTexto()?.let { nomeLegivel(campo) to it }
            }
            detalhes != null -> detalhes.entries.mapNotNull { (campo, valor) ->
                valor.comoTexto()?.let { nomeLegivel(campo) to it }
            }
            detalhesBrutos != null -> detalhesBrutos.comoTexto()?.let { listOf("Detalhes" to it) }.orEmpty()
            else -> emptyList()
        }

        val campos = linha.entries.mapNotNull { (chave, valor) ->
            valor.comoTexto()?.let { chave to it }
        }

        val usuario = usuarioId?.let { nomePorUsuario[it]?.takeIf { nome -> nome.isNotEmpty() } }

        val situacaoAnterior = antes?.get("status")?.comoTexto()
        val situacaoPosterior = (depois ?: registroInserido)?.get("status")?.comoTexto()
        val categoria = classificar(acao, tabela, situacaoAnterior, situacaoPosterior)

        var chave = id ?: "linha-$ordem"
        if (!chavesUsadas.add(chave)) {
            chave = "$chave-$ordem"
            chavesUsadas.add(chave)
        }

        val busca = normalizarTexto(
            listOfNotNull(
                usuario, acao, tabela, registroId, descricao, equipamentoTexto,
                montarTitulo(categoria, acao, tabela), categoria.rotulo,
                equipamentoRow?.etiqueta, equipamentoId?.toString(),
                emprestimoId?.toString(), ocorrenciaId?.toString()
            ).plus(adicionais.map { "${it.first} ${it.second}" })
                .joinToString(" ")
        )

        RegistroAuditoria(
            chave = chave,
            titulo = montarTitulo(categoria, acao, tabela),
            id = id,
            usuarioId = usuarioId,
            usuario = usuario,
            acao = acao,
            tabela = tabela,
            registroId = registroId,
            descricao = descricao,
            equipamentoId = equipamentoId,
            equipamento = equipamentoTexto,
            emprestimoId = emprestimoId,
            ocorrenciaId = ocorrenciaId,
            dataTexto = formatarDataHora(dataTexto),
            dataMs = instanteEmMs(dataTexto),
            categoria = categoria,
            anterior = situacaoAnterior,
            posterior = situacaoPosterior,
            adicionais = adicionais,
            campos = campos,
            textoBusca = busca,
            idNumerico = id?.toLongOrNull(),
            ordem = ordem
        )
    }

    // Mais recente primeiro. Sem data vai para o fim; empates seguem o id (maior = mais novo).
    return registros.sortedWith(
        compareByDescending<RegistroAuditoria> { it.dataMs ?: Long.MIN_VALUE }
            .thenByDescending { it.idNumerico ?: Long.MIN_VALUE }
            .thenByDescending { it.ordem }
    )
}

/** Totais do painel de auditoria. Cada registro entra em uma única categoria. */
internal fun calcularResumoAuditoria(registros: List<RegistroAuditoria>, agoraMs: Long): ResumoAuditoria {
    val limite = agoraMs - 7L * 24L * 60L * 60L * 1000L
    fun contar(c: CategoriaAuditoria) = registros.count { it.categoria == c }
    return ResumoAuditoria(
        total = registros.size,
        recentes = registros.count { (it.dataMs ?: Long.MIN_VALUE) >= limite },
        emprestimos = contar(CategoriaAuditoria.EMPRESTIMO),
        devolucoes = contar(CategoriaAuditoria.DEVOLUCAO),
        equipamentos = contar(CategoriaAuditoria.EQUIPAMENTO),
        ocorrencias = contar(CategoriaAuditoria.OCORRENCIA),
        administrativas = contar(CategoriaAuditoria.ADMINISTRATIVA),
        outras = contar(CategoriaAuditoria.OUTRAS)
    )
}

/**
 * Pesquisa, categoria, usuário e período trabalham juntos (todos precisam combinar).
 * A pesquisa ignora acentos e maiúsculas; cada palavra digitada precisa aparecer no registro.
 */
internal fun filtrarAuditoria(
    registros: List<RegistroAuditoria>,
    consulta: String,
    categoria: CategoriaAuditoria?,
    usuarioId: String?,
    intervalo: IntervaloRelatorio
): List<RegistroAuditoria> {
    val palavras = normalizarTexto(consulta).split(' ').filter { it.isNotEmpty() }
    return registros.filter { r ->
        (categoria == null || r.categoria == categoria) &&
                (usuarioId == null || r.usuarioId == usuarioId) &&
                dentroDoIntervalo(r.dataMs, intervalo) &&
                palavras.all { it in r.textoBusca }
    }
}

private fun mensagemAuditoria(erro: Throwable): String {
    val texto = (erro.message ?: "").lowercase()
    return when {
        "usuario_e_gestao" in texto ->
            "Não foi possível confirmar o acesso da Gestão no servidor. Avise o suporte técnico."
        "auditoria" in texto &&
                ("does not exist" in texto || "42p01" in texto || "schema cache" in texto || "could not find the table" in texto) ->
            "A tabela de auditoria não foi encontrada no Supabase. Avise o suporte técnico."
        "permission denied" in texto || "42501" in texto ->
            "Esta conta não tem permissão para ler os registros de auditoria."
        else -> traduzirErro(erro)
    }
}

/**
 * Módulo de consulta do histórico e da auditoria. SOMENTE LEITURA: não existe nenhum método que grave,
 * altere ou apague registros de auditoria.
 */
class GestaoAuditoriaViewModel(
    private val gestao: GestaoRepository = GestaoRepository(),
    private val auditoria: AuditoriaRepository = AuditoriaRepository(),
    private val usuarios: UsuarioRepository = UsuarioRepository(),
    private val equipamentos: EquipamentoRepository = EquipamentoRepository(),
    private val emprestimos: EmprestimoRepository = EmprestimoRepository(),
    private val defeitos: DefeitoRepository = DefeitoRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(GestaoAuditoriaUiState())
    val state = _state.asStateFlow()

    private var trabalho: Job? = null
    private var ultimaLeituraMs = 0L

    /**
     * Lê os registros. Sem forcar, reaproveita uma leitura feita há menos de 60 s (evita consultas à toa
     * ao voltar para a tela). forcar = true é usado pelo botão de atualizar e por "Tentar novamente".
     * Os dados já exibidos são mantidos enquanto a atualização acontece ou se ela falhar.
     */
    fun carregar(forcar: Boolean = false) {
        val atual = _state.value
        if (!forcar) {
            if (trabalho?.isActive == true || atual.acessoNegado) return
            val recente = System.currentTimeMillis() - ultimaLeituraMs < 60_000L
            if (atual.registros != null && atual.erro == null && recente) return
        }

        trabalho?.cancel()
        trabalho = viewModelScope.launch {
            val base = _state.value
            _state.value = base.copy(
                carregando = base.registros == null,
                atualizando = base.registros != null,
                erro = null,
                acessoNegado = false
            )

            runCatching {
                if (gestao.usuarioEGestao()) lerTudo() else null
            }.onSuccess { novo ->
                if (novo == null) {
                    _state.value = GestaoAuditoriaUiState(carregando = false, acessoNegado = true)
                } else {
                    ultimaLeituraMs = System.currentTimeMillis()
                    _state.value = novo
                }
            }.onFailure { erro ->
                if (erro is CancellationException) throw erro
                _state.value = _state.value.copy(
                    carregando = false,
                    atualizando = false,
                    erro = mensagemAuditoria(erro)
                )
            }
        }
    }

    private suspend fun <T> opcional(bloco: suspend () -> T): T? =
        try {
            bloco()
        } catch (erro: CancellationException) {
            throw erro
        } catch (erro: Throwable) {
            null
        }

    /** Só a auditoria é obrigatória; o resto apenas enriquece (nomes e equipamentos). */
    private suspend fun lerTudo(): GestaoAuditoriaUiState = coroutineScope {
        val listaAuditoria = async { auditoria.listarRecentes() }
        val listaUsuarios = async { opcional { usuarios.listar() } }
        val listaEquipamentos = async { opcional { equipamentos.listar() } }
        val listaEmprestimos = async { opcional { emprestimos.listarGestao() } }
        val listaDefeitos = async { opcional { defeitos.listarGestao() } }

        val linhas = listaAuditoria.await()
        val us = listaUsuarios.await()
        val eq = listaEquipamentos.await()

        val registros = montarRegistrosAuditoria(
            linhas = linhas,
            usuarios = us,
            equipamentos = eq,
            emprestimos = listaEmprestimos.await(),
            defeitos = listaDefeitos.await()
        )

        GestaoAuditoriaUiState(
            carregando = false,
            atualizando = false,
            acessoNegado = false,
            erro = null,
            registros = registros,
            resumo = calcularResumoAuditoria(registros, System.currentTimeMillis()),
            usuariosIndisponiveis = us == null,
            equipamentosIndisponiveis = eq == null,
            possivelLimite = linhas.size >= 1000,
            atualizadoEm = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        )
    }
}