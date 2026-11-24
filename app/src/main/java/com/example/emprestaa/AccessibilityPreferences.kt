package com.example.emprestaa

import android.content.SharedPreferences

/**
 * Tamanho do texto escolhido pelo professor, em porcentagem.
 * O percentual multiplica o tamanho de fonte do sistema (aplicado em EmprestaAppRoot).
 * Valores salvos antigos ("Padrao", "Grande", "MuitoGrande") são convertidos automaticamente.
 */
enum class TamanhoTexto(val valorSalvo: String, val percentual: Int) {
    P100("100", 100),
    P125("125", 125),
    P150("150", 150),
    P175("175", 175),
    P200("200", 200);

    val rotulo: String get() = "$percentual%"
    val fator: Float get() = percentual / 100f

    companion object {
        val PADRAO: TamanhoTexto = P100

        fun deValorSalvo(valor: String?): TamanhoTexto = when (valor) {
            "Padrao" -> P100
            "Grande" -> P125
            "MuitoGrande" -> P150
            else -> entries.firstOrNull { it.valorSalvo == valor } ?: P100
        }
    }
}

/** Preferências de acessibilidade salvas no aparelho. */
data class PreferenciasAcessibilidade(
    val tamanhoTexto: TamanhoTexto = TamanhoTexto.PADRAO,
    val altoContraste: Boolean = false,
    val toqueAmpliado: Boolean = false,
    val avisosPersistentes: Boolean = false
)

/**
 * Lê e grava as preferências no mesmo SharedPreferences que o app já usa
 * ("empresta-ae-local"). O logout só remove "supabase_user_id" e "nome_professor",
 * então estas chaves continuam salvas depois de sair e entrar novamente.
 */
object AcessibilidadeStorage {
    const val ARQUIVO = "empresta-ae-local"

    private const val CHAVE_TAMANHO_TEXTO = "tamanho_texto"
    private const val CHAVE_ALTO_CONTRASTE = "contraste_alto"
    private const val CHAVE_TOQUE_AMPLIADO = "toque_ampliado"
    private const val CHAVE_AVISOS_PERSISTENTES = "avisos_persistentes"

    fun carregar(prefs: SharedPreferences): PreferenciasAcessibilidade = PreferenciasAcessibilidade(
        tamanhoTexto = TamanhoTexto.deValorSalvo(prefs.getString(CHAVE_TAMANHO_TEXTO, null)),
        altoContraste = prefs.getBoolean(CHAVE_ALTO_CONTRASTE, false),
        toqueAmpliado = prefs.getBoolean(CHAVE_TOQUE_AMPLIADO, false),
        avisosPersistentes = prefs.getBoolean(CHAVE_AVISOS_PERSISTENTES, false)
    )

    fun salvar(prefs: SharedPreferences, preferencias: PreferenciasAcessibilidade) {
        prefs.edit()
            .putString(CHAVE_TAMANHO_TEXTO, preferencias.tamanhoTexto.valorSalvo)
            .putBoolean(CHAVE_ALTO_CONTRASTE, preferencias.altoContraste)
            .putBoolean(CHAVE_TOQUE_AMPLIADO, preferencias.toqueAmpliado)
            .putBoolean(CHAVE_AVISOS_PERSISTENTES, preferencias.avisosPersistentes)
            .apply()
    }
}