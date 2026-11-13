package com.wenes.escala.automacao

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wenes.escala.EscalaApp
import kotlinx.coroutines.runBlocking

/**
 * Toca o alarme como o app Relógio: som no canal de ALARME, vibração e notificação em tela cheia.
 * Para sozinho depois de [Notificacoes.ALARME_DURACAO_MS].
 */
class SomAlarmeService : Service() {

    private var player: MediaPlayer? = null
    private var vibrador: Vibrator? = null
    private val relogio = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = runBlocking { (application as EscalaApp).repositorio.atual() }
        ServiceCompat.startForeground(
            this, Notificacoes.ID_ALARME, Notificacoes.notificacaoAlarme(this, config),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        tocar()
        relogio.removeCallbacksAndMessages(null)
        relogio.postDelayed({ stopSelf() }, Notificacoes.ALARME_DURACAO_MS)
        return START_NOT_STICKY
    }

    private fun tocar() {
        if (player != null) return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@SomAlarmeService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "Falha ao tocar o alarme", it) }
        vibrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600, 800), 0))
    }

    override fun onDestroy() {
        relogio.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrador?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SomAlarmeService"

        fun iniciar(contexto: Context) {
            ContextCompat.startForegroundService(contexto, Intent(contexto, SomAlarmeService::class.java))
        }

        fun parar(contexto: Context) {
            contexto.stopService(Intent(contexto, SomAlarmeService::class.java))
        }
    }
}
