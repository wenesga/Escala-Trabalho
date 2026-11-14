package com.wenes.escala.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wenes.escala.automacao.Agendador
import java.time.LocalDateTime

/**
 * O que o sistema pode bloquear e fazer o alarme falhar. Mesma ideia do Ciclo Treino:
 * mostrar só o que falta, com um botão para liberar, e um teste que toca em 10 segundos.
 */
private enum class Permissao(val titulo: String, val texto: String) {
    NOTIFICACOES("Notificações", "Sem elas o alarme não aparece."),
    ALARME_EXATO("Alarmes e lembretes", "Para o alarme tocar na hora certa."),
    TELA_CHEIA("Tela cheia", "Para o alarme ocupar a tela toda."),
    XIAOMI(
        "Xiaomi: janelas",
        "Ligue \"Mostrar na tela de bloqueio\" e \"Abrir janelas em segundo plano\".",
    ),
    BATERIA("Bateria sem restrição", "Para o sistema não encerrar o app."),
    ;

    /** Algumas só existem em certos aparelhos ou versões do Android. */
    fun vale(): Boolean = when (this) {
        XIAOMI -> Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        else -> true
    }

    fun concedida(contexto: Context): Boolean = when (this) {
        NOTIFICACOES -> Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ALARME_EXATO -> Agendador.podeExato(contexto)
        TELA_CHEIA -> Build.VERSION.SDK_INT < 34 ||
            contexto.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        XIAOMI -> opXiaomiLiberada(contexto, 10020) && opXiaomiLiberada(contexto, 10021)
        BATERIA -> contexto.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(contexto.packageName)
    }
}

/** Lê as permissões próprias da Xiaomi (códigos 10020 e 10021); se não der para ler, considera liberada. */
private fun opXiaomiLiberada(contexto: Context, op: Int): Boolean = runCatching {
    val ops = contexto.getSystemService(AppOpsManager::class.java)
    val metodo = AppOpsManager::class.java.getMethod(
        "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
    )
    metodo.invoke(ops, op, android.os.Process.myUid(), contexto.packageName) as Int == AppOpsManager.MODE_ALLOWED
}.getOrDefault(true)

private fun abrirConfiguracao(contexto: Context, permissao: Permissao) {
    val pacote = Uri.parse("package:${contexto.packageName}")
    val detalhes = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pacote)
    val intent = when {
        permissao == Permissao.ALARME_EXATO && Build.VERSION.SDK_INT >= 31 ->
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pacote)
        permissao == Permissao.TELA_CHEIA && Build.VERSION.SDK_INT >= 34 ->
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pacote)
        permissao == Permissao.BATERIA -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pacote)
        permissao == Permissao.XIAOMI -> Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", contexto.packageName)
        else -> detalhes
    }
    runCatching { contexto.startActivity(intent) }.onFailure { contexto.startActivity(detalhes) }
}

/** Aviso na tela principal quando falta liberar algo do alarme (só a primeira que falta, com o botão Permitir). */
@Composable
fun AvisoDePermissao(alarmeLigado: Boolean) {
    if (!alarmeLigado) return
    val contexto = LocalContext.current
    var versao by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        versao++
        onPauseOrDispose { }
    }
    val falta = remember(versao) { Permissao.entries.firstOrNull { it.vale() && !it.concedida(contexto) } } ?: return
    val pedirNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { versao++ }

    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Alarme sem garantia: ${falta.titulo}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                falta.texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(
                onClick = {
                    if (falta == Permissao.NOTIFICACOES && Build.VERSION.SDK_INT >= 33) {
                        pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        abrirConfiguracao(contexto, falta)
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Permitir") }
        }
    }
}

/** Cartão do alarme em Ajustes: o que falta liberar e o botão Testar. */
@Composable
fun AlarmeGarantido() {
    val contexto = LocalContext.current
    var versao by remember { mutableIntStateOf(0) }
    // Ao voltar da tela de configuração do sistema, confere de novo.
    LifecycleResumeEffect(Unit) {
        versao++
        onPauseOrDispose { }
    }
    val faltam = remember(versao) { Permissao.entries.filter { it.vale() && !it.concedida(contexto) } }
    val pedirNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { versao++ }

    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alarme garantido", style = MaterialTheme.typography.titleMedium)
            if (faltam.isEmpty()) {
                Text(
                    "Tudo liberado: o alarme ocupa a tela toda e o sistema não o encerra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            faltam.forEach { permissao ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(permissao.titulo, style = MaterialTheme.typography.titleSmall)
                        Text(
                            permissao.texto,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        if (permissao == Permissao.NOTIFICACOES && Build.VERSION.SDK_INT >= 33) {
                            pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            abrirConfiguracao(contexto, permissao)
                        }
                    }) { Text("Permitir") }
                }
            }
            TextButton(
                onClick = {
                    Agendador.agendarSoneca(contexto, LocalDateTime.now().plusSeconds(10), Agendador.TIPO_TESTE)
                    Toast.makeText(contexto, "O alarme toca em 10 segundos", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Testar") }
        }
    }
}
