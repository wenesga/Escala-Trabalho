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
import androidx.glance.layout.Column
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

    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(fundo).cornerRadius(24.dp)
            .padding(16.dp).clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Hoje · ${Formato.dia(hoje)}", style = TextStyle(color = texto, fontSize = 12.sp))
        Text(
            if (trabalha) "Trabalho" else "Folga",
            style = TextStyle(color = texto, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
        val detalhe = when {
            !c.configurado -> "Abra o app"
            trabalha -> Formato.horario(c)
            else -> Escala.proximoTrabalho(c, hoje.plusDays(1))?.let { "Próximo: ${Formato.dia(it)}" } ?: ""
        }
        if (detalhe.isNotEmpty()) Text(detalhe, style = TextStyle(color = texto, fontSize = 14.sp))
    }
}

class EscalaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EscalaWidget()
}
