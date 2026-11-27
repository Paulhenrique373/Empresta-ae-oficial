package com.example.emprestaa

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.emprestaa.data.DocumentoExportacao
import com.example.emprestaa.data.SecaoExportacao
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal enum class FormatoExportacao(
    val extensao: String,
    val mime: String,
    val rotulo: String
) {
    PDF("pdf", "application/pdf", "PDF"),
    CSV("csv", "text/csv", "CSV")
}

private const val PASTA_RELATORIOS = "relatorios"
private const val UMA_HORA_MS = 3_600_000L

/**
 * Gera o arquivo na pasta de cache do app (pasta "relatorios", liberada no
 * file_paths.xml). Chame fora da thread principal.
 * O conteúdo vem do documento montado com os dados realmente carregados.
 */
internal fun gerarArquivoRelatorio(
    contexto: Context,
    documento: DocumentoExportacao,
    formato: FormatoExportacao,
    nomeBase: String
): File {

    val pasta = File(contexto.cacheDir, PASTA_RELATORIOS).apply { mkdirs() }

    // Remove relatórios antigos (mais de 1 hora) para não acumular arquivos.
    pasta.listFiles()?.forEach {
        if (System.currentTimeMillis() - it.lastModified() > UMA_HORA_MS) it.delete()
    }

    val carimbo = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    val arquivo = File(pasta, "relatorio_${nomeBase}_$carimbo.${formato.extensao}")

    when (formato) {
        FormatoExportacao.CSV -> arquivo.writeText(documentoParaCsv(documento), Charsets.UTF_8)
        FormatoExportacao.PDF -> gravarPdf(documento, arquivo)
    }

    return arquivo
}

/** Abre a janela de compartilhamento do Android com o arquivo gerado. */
internal fun compartilharArquivoRelatorio(
    contexto: Context,
    arquivo: File,
    formato: FormatoExportacao,
    titulo: String
) {
    val uri = FileProvider.getUriForFile(
        contexto,
        "${contexto.packageName}.files",
        arquivo
    )

    val envio = Intent(Intent.ACTION_SEND).apply {
        type = formato.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, titulo)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    contexto.startActivity(
        Intent.createChooser(envio, "Compartilhar relatório")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/* ------------------------------------------------------------------ */
/* CSV                                                                  */
/* ------------------------------------------------------------------ */

/** Texto da célula: protege contra fórmulas de planilha e escapa aspas e separadores. */
private fun celulaCsv(valor: String): String {
    var texto = valor.replace("\r", " ").replace("\n", " ")

    if (texto.isNotEmpty() && texto[0] in "=+-@") {
        texto = "'$texto"
    }

    return if (texto.contains(';') || texto.contains('"')) {
        "\"" + texto.replace("\"", "\"\"") + "\""
    } else {
        texto
    }
}

/**
 * CSV com separador ponto e vírgula e BOM UTF-8
 * (o Excel em português abre com colunas e acentos corretos).
 */
internal fun documentoParaCsv(documento: DocumentoExportacao): String {

    val saida = StringBuilder("\uFEFF")

    fun linha(colunas: List<String>) {
        saida.append(colunas.joinToString(";") { celulaCsv(it) }).append("\r\n")
    }

    linha(listOf("Relatório", documento.titulo))
    linha(listOf("Gerado em", documento.geradoEm))
    linha(listOf("Período", documento.periodo))

    documento.secoes.forEach { secao ->
        linha(emptyList())
        linha(listOf(secao.titulo))
        secao.nota?.let { linha(listOf(it)) }
        linha(secao.cabecalho)
        secao.linhas.forEach { linha(it) }
    }

    return saida.toString()
}

/* ------------------------------------------------------------------ */
/* PDF (android.graphics.pdf.PdfDocument, sem biblioteca extra)         */
/* ------------------------------------------------------------------ */

private const val PAGINA_LARGURA = 842 // A4 em paisagem (pontos)
private const val PAGINA_ALTURA = 595
private const val MARGEM = 36f

private class PaginadorPdf(private val documentoPdf: PdfDocument, private val titulo: String) {

    private var pagina: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var numero = 0
    var y = MARGEM
        private set

    private val criarTinta = { tamanho: Float, negrito: Boolean, cor: Int ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tamanho
            color = cor
            typeface = if (negrito) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    val pTitulo = criarTinta(18f, true, Color.BLACK)
    val pSecao = criarTinta(13f, true, Color.rgb(20, 60, 120))
    val pTexto = criarTinta(10f, false, Color.BLACK)
    val pNota = criarTinta(9f, false, Color.DKGRAY)
    val pCelula = criarTinta(9f, false, Color.BLACK)
    val pCabecalho = criarTinta(9f, true, Color.BLACK)
    private val pLinha = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
    private val pFundo = Paint().apply { color = Color.rgb(232, 238, 247) }
    private val pRodape = criarTinta(8f, false, Color.GRAY)

    val larguraUtil: Float get() = PAGINA_LARGURA - 2 * MARGEM

    private fun novaPagina() {
        finalizarPagina()
        numero++
        val info = PdfDocument.PageInfo.Builder(PAGINA_LARGURA, PAGINA_ALTURA, numero).create()
        val nova = documentoPdf.startPage(info)
        pagina = nova
        canvas = nova.canvas
        y = MARGEM
    }

    private fun finalizarPagina() {
        val atual = pagina ?: return
        canvas?.drawText(
            "Empresta Aê · $titulo · Página $numero",
            MARGEM,
            PAGINA_ALTURA - 16f,
            pRodape
        )
        documentoPdf.finishPage(atual)
        pagina = null
        canvas = null
    }

    private fun garantir(altura: Float) {
        if (pagina == null || y + altura > PAGINA_ALTURA - MARGEM) novaPagina()
    }

    fun quebrar(texto: String, tinta: Paint, largura: Float): List<String> {
        val linhas = mutableListOf<String>()

        texto.split('\n').forEach { paragrafo ->
            var atual = ""

            paragrafo.split(' ').forEach { palavra ->
                var restante = palavra

                // Palavra maior que a coluna: corta em pedaços.
                while (tinta.measureText(restante) > largura && restante.length > 1) {
                    val cabem = tinta.breakText(restante, true, largura, null)
                        .coerceAtLeast(1)

                    if (atual.isNotEmpty()) {
                        linhas.add(atual)
                        atual = ""
                    }

                    linhas.add(restante.substring(0, cabem))
                    restante = restante.substring(cabem)
                }

                val tentativa = if (atual.isEmpty()) restante else "$atual $restante"

                if (tinta.measureText(tentativa) <= largura) {
                    atual = tentativa
                } else {
                    if (atual.isNotEmpty()) linhas.add(atual)
                    atual = restante
                }
            }

            linhas.add(atual)
        }

        return linhas
    }

    fun paragrafo(texto: String, tinta: Paint, espacoDepois: Float = 4f) {
        val altura = tinta.textSize + 3f

        quebrar(texto, tinta, larguraUtil).forEach { linha ->
            garantir(altura)
            canvas?.drawText(linha, MARGEM, y + tinta.textSize, tinta)
            y += altura
        }

        y += espacoDepois
    }

    fun tabela(cabecalho: List<String>, linhas: List<List<String>>) {

        val colunas = cabecalho.size
        if (colunas == 0) return

        // Largura proporcional ao maior texto de cada coluna (com mínimo e máximo).
        val pesos = FloatArray(colunas) { indice ->
            val maior = maxOf(
                cabecalho[indice].length,
                linhas.take(60).maxOfOrNull { it.getOrElse(indice) { "" }.length } ?: 0
            )
            maior.coerceIn(6, 40).toFloat()
        }

        val soma = pesos.sum()
        val larguras = FloatArray(colunas) { larguraUtil * pesos[it] / soma }
        val recuo = 3f
        val alturaLinha = pCelula.textSize + 3f

        // Quebra o texto de cada célula e calcula a altura da linha.
        fun medir(celulas: List<String>, pincel: Paint): Pair<List<List<String>>, Float> {
            val quebradas = (0 until colunas).map { c ->
                quebrar(celulas.getOrElse(c) { "" }, pincel, larguras[c] - 2 * recuo)
            }
            val altura = quebradas.maxOf { it.size } * alturaLinha + 4f
            return Pair(quebradas, altura)
        }

        // Desenha uma linha já medida; a página já tem espaço para ela.
        fun desenhar(
            quebradas: List<List<String>>,
            altura: Float,
            pincel: Paint,
            fundo: Boolean
        ) {
            val cv = canvas ?: return
            val topo = y

            if (fundo) {
                cv.drawRect(MARGEM, topo, MARGEM + larguraUtil, topo + altura, pFundo)
            }

            var x = MARGEM

            for (c in 0 until colunas) {
                quebradas[c].forEachIndexed { i, texto ->
                    cv.drawText(
                        texto,
                        x + recuo,
                        topo + 2f + pincel.textSize + i * alturaLinha,
                        pincel
                    )
                }
                x += larguras[c]
            }

            cv.drawLine(MARGEM, topo + altura, MARGEM + larguraUtil, topo + altura, pLinha)
            y = topo + altura
        }

        val (cabQuebrado, cabAltura) = medir(cabecalho, pCabecalho)

        // Espaço para o cabeçalho e ao menos uma linha antes de começar.
        garantir(cabAltura + alturaLinha * 3)
        desenhar(cabQuebrado, cabAltura, pCabecalho, true)

        linhas.forEach { linha ->
            val (quebrado, altura) = medir(linha, pCelula)

            // Não cabe: nova página, repetindo o cabeçalho da tabela.
            if (pagina == null || y + altura > PAGINA_ALTURA - MARGEM) {
                novaPagina()
                desenhar(cabQuebrado, cabAltura, pCabecalho, true)
            }

            desenhar(quebrado, altura, pCelula, false)
        }

        y += 8f
    }

    fun espaco(valor: Float) {
        y += valor
    }

    fun iniciar() {
        novaPagina()
    }

    fun fechar() {
        finalizarPagina()
    }
}

private fun secaoPdf(p: PaginadorPdf, secao: SecaoExportacao) {
    p.paragrafo(secao.titulo, p.pSecao, 3f)
    secao.nota?.let { p.paragrafo(it, p.pNota, 3f) }
    p.tabela(secao.cabecalho, secao.linhas)
}

private fun gravarPdf(documento: DocumentoExportacao, arquivo: File) {

    val pdf = PdfDocument()

    try {
        val p = PaginadorPdf(pdf, documento.titulo)
        p.iniciar()

        p.paragrafo(documento.titulo, p.pTitulo, 6f)
        p.paragrafo("Gerado em: ${documento.geradoEm}", p.pTexto, 1f)
        p.paragrafo("Período: ${documento.periodo}", p.pTexto, 10f)

        documento.secoes.forEach { secaoPdf(p, it) }

        p.fechar()

        FileOutputStream(arquivo).use { pdf.writeTo(it) }

    } finally {
        pdf.close()
    }
}