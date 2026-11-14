package com.wenes.escala

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.wenes.escala.automacao.AcaoAlarmeReceiver
import com.wenes.escala.automacao.Agendador
import com.wenes.escala.automacao.Notificacoes
import com.wenes.escala.automacao.SomAlarmeService
import com.wenes.escala.data.Config
import com.wenes.escala.data.CorDestaque
import com.wenes.escala.data.Formato
import com.wenes.escala.ui.EscalaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Alarme em tela cheia, por cima da tela de bloqueio. */
class AlarmeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val app = application as EscalaApp
        // O som para sozinho em 10 min; a tela fecha junto.
        lifecycleScope.launch {
            delay(Notificacoes.ALARME_DURACAO_MS)
            finish()
        }

        val tipo = intent.getStringExtra(Agendador.EXTRA_TIPO) ?: Agendador.TIPO_ENTRADA

        setContent {
            val config by produceState<Config?>(null) { value = app.repositorio.atual() }
            EscalaTheme(config?.cor ?: CorDestaque.AZUL) {
                TelaAlarme(
                    config = config,
                    saida = tipo == Agendador.TIPO_SAIDA,
                    teste = tipo == Agendador.TIPO_TESTE,
                    aoAdiar = {
                        AcaoAlarmeReceiver.soneca(this, tipo)
                        finish()
                    },
                    aoParar = {
                        SomAlarmeService.parar(this)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun TelaAlarme(config: Config?, saida: Boolean, teste: Boolean, aoAdiar: () -> Unit, aoParar: () -> Unit) {
    var agora by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            agora = LocalTime.now()
            delay(1_000)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Text("Escala", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(Formato.hora(agora), style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        teste -> "Teste do alarme"
                        saida -> "Folga"
                        else -> "Hoje é dia de trabalho"
                    },
                    style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                )
                // O alarme de saída só diz "Folga": o resto a pessoa já sabe.
                if (config != null && !saida) {
                    Text(
                        when {
                            teste -> "Está tudo certo"
                            else -> Formato.horario(config)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1.5f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(onClick = aoAdiar, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                        Icon(Icons.Outlined.Snooze, null, Modifier.padding(end = 8.dp))
                        Text("Soneca")
                    }
                    Button(onClick = aoParar, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                        Icon(Icons.Outlined.AlarmOff, null, Modifier.padding(end = 8.dp))
                        Text("Parar")
                    }
                }
            }
        }
    }
}
