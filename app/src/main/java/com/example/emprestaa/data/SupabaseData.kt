
package com.example.emprestaa.data

import android.content.Context
import com.example.emprestaa.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object SupabaseProvider {

    private var appContext: Context? = null

    /**
     * Chamado no MainActivity.onCreate, antes de qualquer uso do cliente.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val client by lazy {
        require(
            BuildConfig.SUPABASE_URL.isNotBlank() &&
                    BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
        ) {
            "Configure SUPABASE_URL e SUPABASE_ANON_KEY em local.properties."
        }

        val contexto = appContext

        createSupabaseClient(
            BuildConfig.SUPABASE_URL,
            BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                // Guarda a sessão no aparelho para o usuário continuar logado.
                if (contexto != null) {
                    sessionManager = PrefsSessionManager(contexto)
                }
            }

            install(Postgrest)
        }
    }
}

/**
 * Guarda a sessão do Supabase em SharedPreferences privado do app.
 */
class PrefsSessionManager(context: Context) : SessionManager {

    private val prefs = context.getSharedPreferences(
        "empresta-ae-sessao",
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveSession(session: UserSession) {
        prefs.edit()
            .putString(
                "sessao",
                json.encodeToString(UserSession.serializer(), session)
            )
            .apply()
    }

    override suspend fun loadSession(): UserSession? =
        prefs.getString("sessao", null)?.let { texto ->
            runCatching {
                json.decodeFromString(UserSession.serializer(), texto)
            }.getOrNull()
        }

    override suspend fun deleteSession() {
        prefs.edit().remove("sessao").apply()
    }
}

@Serializable
data class UsuarioRow(
    @SerialName("id_usuario")
    val id: String,

    @SerialName("nome_completo")
    val nome: String,

    val ativo: Boolean
)

@Serializable
data class PerfilRow(
    @SerialName("nome_perfil")
    val nome: String
)

@Serializable
data class UsuarioPerfilRow(
    @SerialName("id_perfil")
    val perfilId: Long
)

@Serializable
data class EquipamentoRow(
    @SerialName("id_equipamento")
    val id: Long,

    val marca: String,
    val modelo: String,

    @SerialName("numero_etiqueta")
    val etiqueta: String,

    @SerialName("tipo_equipamento")
    val tipo: String,

    @SerialName("uso_tecnico")
    val usoTecnico: Boolean,

    val status: String,
    val observacao: String? = null
)

@Serializable
data class EmprestimoRow(
    @SerialName("id_emprestimo")
    val id: Long,

    // Pode ser nulo caso existam registros antigos ou incompletos no banco.
    @SerialName("id_usuario")
    val usuarioId: String? = null,

    @SerialName("id_equipamento")
    val equipamentoId: Long,

    @SerialName("data_retirada")
    val retirada: String,

    @SerialName("data_devolucao")
    val devolucao: String? = null,

    val status: String
)

@Serializable
data class DefeitoInsert(
    @SerialName("id_equipamento")
    val equipamentoId: Long,

    @SerialName("id_usuario")
    val usuarioId: String,

    @SerialName("tipo_defeito")
    val tipo: String,

    val descricao: String
)

@Serializable
data class EquipamentoInsert(
    val marca: String,
    val modelo: String,

    @SerialName("numero_etiqueta")
    val etiqueta: String,

    @SerialName("tipo_equipamento")
    val tipo: String,

    @SerialName("uso_tecnico")
    val usoTecnico: Boolean,

    val status: String = "Disponível",
    val observacao: String? = null
)

@Serializable
data class EquipamentoPatch(
    val marca: String,
    val modelo: String,

    @SerialName("tipo_equipamento")
    val tipo: String,

    @SerialName("uso_tecnico")
    val usoTecnico: Boolean,

    val status: String,
    val observacao: String? = null
)

@Serializable
data class DefeitoRow(
    @SerialName("id_defeito")
    val id: Long,

    @SerialName("id_equipamento")
    val equipamentoId: Long,

    @SerialName("id_usuario")
    val usuarioId: String,

    @SerialName("tipo_defeito")
    val tipo: String,

    val descricao: String,

    @SerialName("data_defeito")
    val data: String,

    val status: String
)

@Serializable
data class UsuarioAdminRow(
    @SerialName("id_usuario")
    val id: String,

    @SerialName("nome_completo")
    val nome: String,

    val ativo: Boolean,

    @SerialName("usuario_perfil")
    val perfis: List<UsuarioPerfilAdmin> = emptyList()
)

@Serializable
data class UsuarioPerfilAdmin(
    @SerialName("perfis")
    val perfil: PerfilRow
)

enum class PerfilAcesso(val label: String) {
    PROFESSOR_FGB("Professor FGB"),
    PROFESSOR_TECNICO("Professor Técnico"),
    PAEET("PAEET"),
    GESTAO("Gestão"),
    OUTRO("Outro")
}

data class SessaoUsuario(
    val id: String,
    val nome: String,
    val perfis: Set<PerfilAcesso>
)

class AuthRepository {

    suspend fun entrar(email: String, senha: String): SessaoUsuario {
        val client = SupabaseProvider.client

        client.auth.signInWith(Email) {
            this.email = email
            password = senha
        }

        return try {
            carregarSessao(client)
        } catch (e: Throwable) {
            runCatching {
                client.auth.signOut()
            }
            throw e
        }
    }

    private suspend fun carregarSessao(
        client: SupabaseClient
    ): SessaoUsuario {

        val id = client.auth.currentUserOrNull()?.id
            ?: error("A sessão não foi criada.")

        val usuario = client
            .from("usuarios")
            .select {
                filter {
                    eq("id_usuario", id)
                }
            }
            .decodeList<UsuarioRow>()
            .firstOrNull()
            ?: error("Usuário sem cadastro escolar.")

        check(usuario.ativo) {
            "Conta desativada. Procure a gestão escolar."
        }

        val relacoes = client
            .from("usuario_perfil")
            .select {
                filter {
                    eq("id_usuario", id)
                }
            }
            .decodeList<UsuarioPerfilRow>()

        val perfisEncontrados = relacoes.mapNotNull { relacao ->

            client
                .from("perfis")
                .select {
                    filter {
                        eq("id_perfil", relacao.perfilId)
                    }
                }
                .decodeList<PerfilRow>()
                .firstOrNull()
                ?.nome
        }

        // Qualquer perfil cadastrado é aceito.
        // Nomes fora da lista viram OUTRO.
        val perfis = perfisEncontrados.map { nome ->

            PerfilAcesso.entries.firstOrNull {
                it.label.equals(nome.trim(), ignoreCase = true)
            } ?: PerfilAcesso.OUTRO

        }.toSet()

        check(perfis.isNotEmpty()) {
            "Nenhum perfil foi atribuído à conta. Procure a gestão escolar."
        }

        return SessaoUsuario(
            id = id,
            nome = usuario.nome,
            perfis = perfis
        )
    }

    suspend fun sair() {
        SupabaseProvider.client.auth.signOut()
    }

    /**
     * Avisa quando a sessão existe ou deixou de existir.
     */
    suspend fun observarSessao(
        aoMudar: (perdida: Boolean) -> Unit
    ) {
        SupabaseProvider.client.auth.sessionStatus.collect { status ->

            when (status) {
                is SessionStatus.Authenticated -> aoMudar(false)
                is SessionStatus.NotAuthenticated -> aoMudar(true)
                else -> Unit
            }
        }
    }

    suspend fun recuperar(email: String) =
        SupabaseProvider.client.auth.resetPasswordForEmail(email)
}

class EquipamentoRepository {

    suspend fun listar() =
        SupabaseProvider.client
            .from("equipamentos")
            .select()
            .decodeList<EquipamentoRow>()

    suspend fun cadastrar(item: EquipamentoInsert) {
        SupabaseProvider.client
            .from("equipamentos")
            .insert(item)
    }

    suspend fun alterar(id: Long, item: EquipamentoPatch) {
        SupabaseProvider.client
            .from("equipamentos")
            .update(item) {
                filter {
                    eq("id_equipamento", id)
                }
            }
    }
}

class EmprestimoRepository {

    suspend fun meusAtivos(usuarioId: String) =
        SupabaseProvider.client
            .from("emprestimos")
            .select {
                filter {
                    eq("id_usuario", usuarioId)
                    eq("status", "Ativo")
                }
            }
            .decodeList<EmprestimoRow>()

    suspend fun historico(usuarioId: String) =
        SupabaseProvider.client
            .from("emprestimos")
            .select {
                filter {
                    eq("id_usuario", usuarioId)
                }
            }
            .decodeList<EmprestimoRow>()

    suspend fun listarGestao() =
        SupabaseProvider.client
            .from("emprestimos")
            .select()
            .decodeList<EmprestimoRow>()

    suspend fun retirar(equipamentoId: Long) {
        SupabaseProvider.client.postgrest.rpc(
            "registrar_emprestimo",
            kotlinx.serialization.json.buildJsonObject {
                put(
                    "p_id_equipamento",
                    kotlinx.serialization.json.JsonPrimitive(equipamentoId)
                )
            }
        )
    }

    suspend fun devolver(emprestimoId: Long) {
        SupabaseProvider.client.postgrest.rpc(
            "registrar_devolucao",
            kotlinx.serialization.json.buildJsonObject {
                put(
                    "p_id_emprestimo",
                    kotlinx.serialization.json.JsonPrimitive(emprestimoId)
                )
            }
        )
    }
}

class DefeitoRepository {

    suspend fun registrar(item: DefeitoInsert) {
        SupabaseProvider.client
            .from("defeitos")
            .insert(item)
    }

    suspend fun listar() =
        SupabaseProvider.client
            .from("defeitos")
            .select()
            .decodeList<DefeitoRow>()

    suspend fun atualizarStatus(id: Long, status: String) {
        SupabaseProvider.client
            .from("defeitos")
            .update(
                kotlinx.serialization.json.buildJsonObject {
                    put(
                        "status",
                        kotlinx.serialization.json.JsonPrimitive(status)
                    )
                }
            ) {
                filter {
                    eq("id_defeito", id)
                }
            }
    }
}

class UsuarioRepository {
    suspend fun listar() =
        SupabaseProvider.client
            .from("usuarios")
            .select(
                Columns.raw("*,usuario_perfil(perfis(nome_perfil))")
            )
            .decodeList<UsuarioAdminRow>()
}

class AuditoriaRepository {
    suspend fun listar() =
        SupabaseProvider.client
            .from("auditoria")
            .select()
            .decodeList<kotlinx.serialization.json.JsonObject>()
}

/**
 * Colunas que o painel usa de equipamentos.
 */
@Serializable
data class EquipamentoResumoRow(
    @SerialName("tipo_equipamento")
    val tipo: String? = null,

    val status: String? = null
)

/**
 * Apenas a situação de uma linha de empréstimos ou defeitos.
 */
@Serializable
data class SituacaoRow(
    val status: String? = null
)

/**
 * Usuário reduzido ao necessário para contar professores.
 */
@Serializable
data class UsuarioPerfisRow(
    val ativo: Boolean = false,

    @SerialName("usuario_perfil")
    val perfis: List<UsuarioPerfilAdmin> = emptyList()
)

/**
 * Dados brutos lidos para o painel da Gestão.
 * A RLS do Supabase decide o que cada conta consegue ler.
 */
data class DadosGestao(
    val equipamentos: List<EquipamentoResumoRow>,
    val emprestimos: List<SituacaoRow>,
    val defeitos: List<SituacaoRow>,

    // null quando a conta não conseguiu ler usuários/perfis.
    val professores: List<UsuarioPerfisRow>?
)

/**
 * Repositório do painel da Gestão.
 * Lê somente as colunas necessárias.
 */
class GestaoRepository {

    private val client
        get() = SupabaseProvider.client

    /**
     * Pergunta ao servidor se a conta logada é da Gestão.
     */
    suspend fun usuarioEGestao(): Boolean =
        client.postgrest
            .rpc("usuario_e_gestao")
            .decodeAs<Boolean>()

    /**
     * Lê os dados do painel em paralelo.
     */
    suspend fun carregarDados(): DadosGestao = coroutineScope {

        val e = async {
            client
                .from("equipamentos")
                .select(
                    Columns.raw("tipo_equipamento,status")
                )
                .decodeList<EquipamentoResumoRow>()
        }

        val m = async {
            client
                .from("emprestimos")
                .select(
                    Columns.raw("status")
                )
                .decodeList<SituacaoRow>()
        }

        val d = async {
            client
                .from("defeitos")
                .select(
                    Columns.raw("status")
                )
                .decodeList<SituacaoRow>()
        }

        val u = async {
            try {
                client
                    .from("usuarios")
                    .select(
                        Columns.raw(
                            "ativo,usuario_perfil(perfis(nome_perfil))"
                        )
                    )
                    .decodeList<UsuarioPerfisRow>()

            } catch (erro: CancellationException) {
                throw erro
            } catch (erro: Throwable) {
                null
            }
        }

        DadosGestao(
            equipamentos = e.await(),
            emprestimos = m.await(),
            defeitos = d.await(),
            professores = u.await()
        )
    }
}
