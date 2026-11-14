package com.wenes.escala.automacao

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.updateAll
import com.wenes.escala.EscalaApp
import com.wenes.escala.MainActivity
import com.wenes.escala.data.AgendaGoogle
import com.wenes.escala.data.Config
import com.wenes.escala.data.Escala
import com.wenes.escala.widget.EscalaWidget
import java.time.LocalDateTime
import java.time.ZoneId

/** Agenda o alarme do próximo dia de trabalho e a virada do dia (para o widget). */
object Agendador {

    const val SONECA_MINUTOS = 10L

    /** Qual alarme está tocando: o de antes do trabalho (entrada) ou o do fim do turno (saída). */
    const val EXTRA_TIPO = "tipo"
    const val TIPO_ENTRADA = "entrada"
    const val TIPO_SAIDA = "saida"

    private const val REQ_SAIDA = 5
    private const val REQ_ALARME = 1
    private const val REQ_SONECA = 2
    private const val REQ_MEIA_NOITE = 3
    private const val REQ_ABRIR = 4

    fun podeExato(contexto: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gerenciador(contexto).canScheduleExactAlarms()

    /** Refaz o alarme, a virada do dia e o widget. Chamar após qualquer mudança da escala. */
    suspend fun reagendar(contexto: Context, config: Config? = null, anterior: Config? = null) {
        val c = config ?: (contexto.applicationContext as EscalaApp).repositorio.atual()
        val quando = proximoAlarme(c)
        if (quando == null) {
            gerenciador(contexto).cancel(pendente(contexto, REQ_ALARME))
        } else {
            definirAlarme(contexto, REQ_ALARME, quando)
        }
        val saida = proximaSaida(c)
        if (saida == null) {
            gerenciador(contexto).cancel(pendente(contexto, REQ_SAIDA, TIPO_SAIDA))
        } else {
            definirAlarme(contexto, REQ_SAIDA, saida, TIPO_SAIDA)
        }
        agendarMeiaNoite(contexto)
        runCatching { EscalaWidget().updateAll(contexto) }
        AgendaGoogle.sincronizar(contexto, c, anterior)
    }

    /** O primeiro dia de trabalho, de hoje em diante, cujo horário do alarme ainda não passou. */
    fun proximoAlarme(c: Config, agora: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (!c.configurado || !c.alarmeAtivo) return null
        var dia = agora.toLocalDate()
        repeat(800) {
            if (Escala.trabalha(c, dia)) {
                val quando = dia.atTime(c.alarme)
                if (quando.isAfter(agora)) return quando
            }
            dia = dia.plusDays(1)
        }
        return null
    }

    /** O fim do próximo turno ainda por acabar. Se o turno vira a noite, termina no dia seguinte. */
    fun proximaSaida(c: Config, agora: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (!c.configurado || !c.alarmeSaida) return null
        var dia = agora.toLocalDate().minusDays(1)
        repeat(800) {
            if (Escala.trabalha(c, dia)) {
                val fim = dia.plusDays(if (c.saida <= c.entrada) 1 else 0).atTime(c.saida)
                if (fim.isAfter(agora)) return fim
            }
            dia = dia.plusDays(1)
        }
        return null
    }

    fun agendarSoneca(contexto: Context, quando: LocalDateTime, tipo: String = TIPO_ENTRADA) =
        definirAlarme(contexto, REQ_SONECA, quando, tipo)

    fun cancelarSoneca(contexto: Context) {
        gerenciador(contexto).cancel(pendente(contexto, REQ_SONECA))
    }

    /** setAlarmClock aparece como alarme na barra de status e acorda o celular, como o app Relógio. */
    private fun definirAlarme(contexto: Context, codigo: Int, quando: LocalDateTime, tipo: String = TIPO_ENTRADA) {
        val am = gerenciador(contexto)
        val ms = emMillis(quando)
        if (podeExato(contexto)) {
            val abrir = PendingIntent.getActivity(
                contexto, REQ_ABRIR, Intent(contexto, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(ms, abrir), pendente(contexto, codigo, tipo))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pendente(contexto, codigo, tipo))
        }
    }

    private fun agendarMeiaNoite(contexto: Context) {
        val quando = LocalDateTime.now().toLocalDate().plusDays(1).atTime(0, 0, 5)
        val pi = PendingIntent.getBroadcast(
            contexto, REQ_MEIA_NOITE, Intent(contexto, MeiaNoiteReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        gerenciador(contexto).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, emMillis(quando), pi)
    }

    private fun gerenciador(contexto: Context) = contexto.getSystemService(AlarmManager::class.java)

    private fun pendente(contexto: Context, codigo: Int, tipo: String = TIPO_ENTRADA) = PendingIntent.getBroadcast(
        contexto, codigo, Intent(contexto, AlarmeReceiver::class.java).putExtra(EXTRA_TIPO, tipo),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun emMillis(quando: LocalDateTime) = quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
