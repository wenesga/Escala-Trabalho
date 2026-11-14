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
    val fundo = if (trabalha) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.widgetBackground
    val texto = if (trabalha) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface
    val titulo = if (trabalha) "Trabalho" else "Folga"
    val detalhe = when {
        !c.configurado -> "Abra o app"
        trabalha -> Formato.horario(c)
        else -> Escala.proximoTrabalho(c, hoje.plusDays(1))?.let { "Próximo: ${Formato.dia(it)}" } ?: ""
    }
    val altura = LocalSize.current.height
    val moldura = GlanceModifier.fillMaxSize().appWidgetBackground().background(fundo).cornerRadius(24.dp)
        .clickable(actionStartActivity<MainActivity>())

    when {
        // Baixo: uma linha só, com o título e, na frente dele, o detalhe (próximo dia de trabalho ou horário),
        // que no tamanho largo cabe ao lado e não precisa ir para baixo.
        altura < 70.dp -> Row(
            moldura.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titulo, style = TextStyle(color = texto, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            if (detalhe.isNotEmpty()) {
                Spacer(GlanceModifier.width(16.dp))
                Text(detalhe, style = TextStyle(color = texto, fontSize = 14.sp), maxLines = 1)
            }
        }
        // Médio: a data em cima e, embaixo, o título com o detalhe na frente dele (cabe ao lado, não vai para baixo).
        altura < 110.dp -> Column(
            moldura.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hoje · ${Formato.dia(hoje)}", style = TextStyle(color = texto, fontSize = 12.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, style = TextStyle(color = texto, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                if (detalhe.isNotEmpty()) {
                    Spacer(GlanceModifier.width(16.dp))
                    Text(detalhe, style = TextStyle(color = texto, fontSize = 14.sp), maxLines = 1)
                }
            }
        }
        // Alto: data, título e o detalhe. A letra e o espaço entre as linhas crescem com o tamanho do widget,
        // para não sobrar vazio em cima e embaixo (limitada pela largura, para nada passar da borda).
        else -> {
            val largura = LocalSize.current.width.value
            val titulo_sp = minOf(altura.value * 0.17f, (largura - 32f) / 4.6f).coerceIn(28f, 36f)
            val data_sp = (titulo_sp * 0.40f).coerceIn(12f, 15f)
            // O detalhe ("Próximo: Dom, 15/11") encolhe até caber inteiro numa linha, mesmo no quadrado.
            val detalhe_sp = minOf(titulo_sp * 0.46f, (largura - 32f) / 11f).coerceIn(9f, 17f)
            val respiro = (titulo_sp * 0.16f).dp
            Column(
                moldura.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hoje · ${Formato.dia(hoje)}", style = TextStyle(color = texto, fontSize = data_sp.sp))
                Spacer(GlanceModifier.height(respiro))
                Text(titulo, style = TextStyle(color = texto, fontSize = titulo_sp.sp, fontWeight = FontWeight.Bold))
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
