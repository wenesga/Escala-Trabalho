package com.wenes.escala.automacao

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Roda trabalho suspenso dentro de um receiver, sem deixar o sistema matar o processo antes. */
private fun BroadcastReceiver.emSegundoPlano(bloco: suspend () -> Unit) {
    val pendente = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            bloco()
        } finally {
            pendente.finish()
        }
    }
}

/** Hora do alarme (ou da soneca): toca e já agenda o próximo dia de trabalho. */
class AlarmeReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val app = contexto.applicationContext
        SomAlarmeService.iniciar(app, intent.getStringExtra(Agendador.EXTRA_TIPO) ?: Agendador.TIPO_ENTRADA)
        emSegundoPlano { Agendador.reagendar(app) }
    }
}

/** Virou o dia: o widget troca de Trabalho para Folga (ou o contrário). */
class MeiaNoiteReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val app = contexto.applicationContext
        emSegundoPlano { Agendador.reagendar(app) }
    }
}

/** Reinício do celular, troca de hora ou permissão de alarme concedida: refaz tudo. */
class ReagendarReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val app = contexto.applicationContext
        emSegundoPlano { Agendador.reagendar(app) }
    }
}

/** Botões Parar e Soneca da notificação do alarme. */
class AcaoAlarmeReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        when (intent.action) {
            ACAO_SONECA -> soneca(contexto, intent.getStringExtra(Agendador.EXTRA_TIPO) ?: Agendador.TIPO_ENTRADA)
            ACAO_PARAR -> SomAlarmeService.parar(contexto)
        }
    }

    companion object {
        const val ACAO_PARAR = "com.wenes.escala.PARAR_ALARME"
        const val ACAO_SONECA = "com.wenes.escala.SONECA_ALARME"

        fun soneca(contexto: Context, tipo: String) {
            SomAlarmeService.parar(contexto)
            Agendador.agendarSoneca(contexto, LocalDateTime.now().plusMinutes(Agendador.SONECA_MINUTOS), tipo)
        }
    }
}
