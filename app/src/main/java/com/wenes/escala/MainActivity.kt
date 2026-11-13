package com.wenes.escala

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wenes.escala.data.CorDestaque
import com.wenes.escala.automacao.Agendador
import com.wenes.escala.ui.EscalaTheme
import com.wenes.escala.ui.Raiz
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissao = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Com a permissão da agenda concedida, já marca os dias de trabalho.
    private val permissaoAgenda = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        lifecycleScope.launch { Agendador.reagendar(application) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as EscalaApp
        // Garante o alarme e o widget em dia sempre que o app abre.
        lifecycleScope.launch { Agendador.reagendar(app) }

        setContent {
            val config by app.repositorio.config.collectAsStateWithLifecycle(initialValue = null)
            EscalaTheme(config?.cor ?: CorDestaque.AZUL) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Raiz(
                        app.repositorio,
                        pedirNotificacao = {
                            if (Build.VERSION.SDK_INT >= 33) permissao.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        pedirAgenda = {
                            permissaoAgenda.launch(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                            )
                        },
                    )
                }
            }
        }
    }
}
