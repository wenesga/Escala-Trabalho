package com.wenes.escala.automacao

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wenes.escala.AlarmeActivity
import com.wenes.escala.R
import com.wenes.escala.data.Config
import com.wenes.escala.data.Formato

object Notificacoes {

    const val CANAL_ALARME = "alarme_escala"
    const val ID_ALARME = 2
    /** Depois disso o alarme para sozinho (som e tela), como no app Relógio. */
    const val ALARME_DURACAO_MS = 10 * 60 * 1000L

    fun criarCanal(contexto: Context) {
        val canal = NotificationChannel(
            CANAL_ALARME,
            contexto.getString(R.string.canal_alarme),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = contexto.getString(R.string.canal_alarme_descricao)
            // O som e a vibração vêm do SomAlarmeService, no canal de alarme (passa pelo Não perturbe).
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun notificacaoAlarme(contexto: Context, c: Config): Notification {
        val tela = PendingIntent.getActivity(
            contexto, 10, Intent(contexto, AlarmeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(contexto, CANAL_ALARME)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("Trabalho")
            .setContentText(Formato.horario(c))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(tela)
            .setFullScreenIntent(tela, true)
            .addAction(0, "Soneca", acao(contexto, 11, AcaoAlarmeReceiver.ACAO_SONECA))
            .addAction(0, "Parar", acao(contexto, 12, AcaoAlarmeReceiver.ACAO_PARAR))
            .setTimeoutAfter(ALARME_DURACAO_MS)
            .build()
    }

    private fun acao(contexto: Context, codigo: Int, nome: String) = PendingIntent.getBroadcast(
        contexto, codigo, Intent(contexto, AcaoAlarmeReceiver::class.java).setAction(nome),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
