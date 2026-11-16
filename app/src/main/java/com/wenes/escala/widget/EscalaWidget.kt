package com.wenes.escala.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.LocalSize
import androidx.glance.LocalContext
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.wenes.escala.EscalaApp
import com.wenes.escala.MainActivity
import com.wenes.escala.data.Config
import com.wenes.escala.data.Escala
import com.wenes.escala.data.Formato
import java.time.LocalDate

class EscalaWidget : GlanceAppWidget() {

    // O tamanho real que o aparelho deu ao widget: o conteúdo se adapta a ele (cada launcher redimensiona de um jeito).
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = (context.applicationContext as EscalaApp).repositorio.atual()
        provideContent { GlanceTheme { Conteudo(config) } }
    }
}

@Composable
private fun Conteudo(c: Config) {
    val hoje = LocalDate.now()
    val trabalha = c.configurado && Escala.trabalha(c, hoje)
    // Fundo neutro nos dois casos; quem avisa é a cor do título: vermelho no trabalho, verde na folga.
    val fundo = GlanceTheme.colors.widgetBackground
    val corTitulo = ColorProvider(if (trabalha) Color(0xFFEF5350) else Color(0xFF66BB6A))
    val texto = GlanceTheme.colors.onSurface
    val titulo = if (trabalha) "Trabalho" else "Folga"
    val detalhe = when {
        !c.configurado -> "Abra o app"
        trabalha -> Formato.horario(c)
        else -> Escala.proximoTrabalho(c, hoje.plusDays(1))?.let { "Próximo: ${Formato.dia(it)}" } ?: ""
    }
    val altura = LocalSize.current.height
    val larg = LocalSize.current.width.value
    // A escala de fonte do aparelho aumenta toda letra em sp: o cálculo de largura precisa contar com ela.
    val escalaFonte = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
    // Estreito (1 ou 2 colunas): textos mais curtos e letra calculada pela largura, para nada ser cortado.
    val estreito = larg < 200f
    val padH = (larg * 0.08f).coerceIn(6f, 16f)
    val disponivel = (larg - 2 * padH).coerceAtLeast(20f)
    // Tamanho de letra para o texto caber inteiro numa linha (0,6 da letra por caractere em negrito, 0,52 no normal).
    fun cabe(t: String, max: Float, min: Float, negrito: Boolean = false) =
        (disponivel / (t.length.coerceAtLeast(1) * (if (negrito) 0.62f else 0.52f * escalaFonte))).coerceIn(min, max)
    val dataTexto = if (estreito) Formato.dia(hoje) else "Hoje · ${Formato.dia(hoje)}"
    val detalheCurto = when {
        !c.configurado -> "Abra o app"
        trabalha -> "${Formato.hora(c.entrada)}–${Formato.hora(c.saida)}"
        else -> Escala.proximoTrabalho(c, hoje.plusDays(1))?.let { "Próx. ${Formato.dia(it)}" } ?: ""
    }
    val detalheUsado = if (estreito) detalheCurto else detalhe
    val moldura = GlanceModifier.fillMaxSize().appWidgetBackground().background(fundo).cornerRadius(24.dp)
        .clickable(actionStartActivity<MainActivity>())

    // Detalhe ao lado do título: abreviado se o widget não é largo e com letra que cabe no que sobra da linha.
    fun detalheDaLinha(tituloSp: Float): Pair<String, Float> {
        val t = if (larg < 340f) detalheCurto else detalhe
        val resto = larg - 32f - titulo.length * 0.55f * escalaFonte * tituloSp - 12f
        return t to (resto / (t.length.coerceAtLeast(1) * 0.48f * escalaFonte)).coerceIn(6f, if (larg >= 340f) 18f else 14f)
    }

    when {
        // Uma coluna só (X1): apenas a letra, T de trabalho e F de folga, grande.
        larg < 100f -> Column(
            moldura,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val letra = titulo.take(1)
            val letra_sp = minOf(altura.value * 0.55f, larg * 0.75f).coerceIn(16f, 48f)
            Text(letra, style = TextStyle(color = corTitulo, fontSize = letra_sp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        // Estreito: uma coluna de textos pequenos, cada um ajustado à largura; a data só aparece se houver altura.
        estreito -> Column(
            moldura.padding(horizontal = padH.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (altura >= 110.dp) {
                Text(dataTexto, style = TextStyle(color = texto, fontSize = cabe(dataTexto, 13f, 5f).sp), maxLines = 1)
            }
            Text(
                titulo,
                style = TextStyle(color = corTitulo, fontSize = cabe(titulo, if (altura < 60.dp) 15f else 20f, 6f, true).sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (detalheUsado.isNotEmpty() && altura >= 50.dp) {
                Text(detalheUsado, style = TextStyle(color = texto, fontSize = cabe(detalheUsado, 14f, 5f).sp), maxLines = 1)
            }
        }
        // Baixo e largo: uma linha só, com o título e, na frente dele, o detalhe.
        altura < 70.dp -> Row(
            moldura.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titulo, style = TextStyle(color = corTitulo, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            val (linhaDetalhe, linhaSp) = detalheDaLinha(20f)
            if (linhaDetalhe.isNotEmpty()) {
                Spacer(GlanceModifier.width(12.dp))
                Text(linhaDetalhe, style = TextStyle(color = texto, fontSize = linhaSp.sp), maxLines = 1)
            }
        }
        // Médio: a data em cima e, embaixo, o título com o detalhe na frente dele.
        altura < 110.dp -> Column(
            moldura.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dataTexto, style = TextStyle(color = texto, fontSize = 12.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val medio_sp = minOf(if (larg >= 340f) 30f else 22f, (larg - 32f) / 7f).coerceAtLeast(14f)
                Text(titulo, style = TextStyle(color = corTitulo, fontSize = medio_sp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                val (linhaDetalhe, linhaSp) = detalheDaLinha(medio_sp)
                if (linhaDetalhe.isNotEmpty()) {
                    Spacer(GlanceModifier.width(12.dp))
                    Text(linhaDetalhe, style = TextStyle(color = texto, fontSize = linhaSp.sp), maxLines = 1)
                }
            }
        }
        // Alto: data, título e o detalhe. A letra e o espaço entre as linhas crescem com o tamanho do widget,
        // para não sobrar vazio em cima e embaixo (limitada pela largura, para nada passar da borda).
        else -> {
            val titulo_sp = minOf(altura.value * 0.17f, (larg - 32f) / 5.4f).coerceIn(16f, 36f)
            val data_sp = (titulo_sp * 0.40f).coerceIn(12f, 15f)
            // O detalhe ("Próximo: Dom, 15/11") encolhe até caber inteiro numa linha, mesmo no quadrado.
            val detalhe_sp = minOf(titulo_sp * 0.46f, (larg - 32f) / 13.8f).coerceIn(9f, 17f)
            val respiro = (titulo_sp * 0.16f).dp
            Column(
                moldura.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dataTexto, style = TextStyle(color = texto, fontSize = data_sp.sp))
                Spacer(GlanceModifier.height(respiro))
                Text(titulo, style = TextStyle(color = corTitulo, fontSize = titulo_sp.sp, fontWeight = FontWeight.Bold))
                if (detalhe.isNotEmpty()) {
                    Spacer(GlanceModifier.height(respiro))
                    Text(detalhe, style = TextStyle(color = texto, fontSize = detalhe_sp.sp), maxLines = 1)
                }
            }
        }
    }
}

class EscalaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EscalaWidget()
}
