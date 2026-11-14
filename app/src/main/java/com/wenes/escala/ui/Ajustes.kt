package com.wenes.escala.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.wenes.escala.data.CorDestaque
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.height
import com.wenes.escala.data.Periodo
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wenes.escala.automacao.Agendador
import com.wenes.escala.data.Config
import com.wenes.escala.data.Formato
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private enum class Campo { INICIO, ENTRADA, SAIDA, ALARME, FERIAS }

@Composable
fun Ajustes(
    c: Config,
    primeiraVez: Boolean,
    aoCor: (CorDestaque) -> Unit,
    aoFerias: (List<Periodo>) -> Unit,
    aoSalvar: (Config) -> Unit,
    aoVoltar: () -> Unit,
) {
    var cor by remember { mutableStateOf(c.cor) }
    var agenda by remember { mutableStateOf(c.agenda) }
    var ferias by remember { mutableStateOf(c.ferias) }
    var alarmeSaida by remember { mutableStateOf(c.alarmeSaida) }
    var minutosAntesSaida by remember { mutableStateOf(c.minutosAntesSaida) }
    var titulo by remember { mutableStateOf(c.titulo) }
    var local by remember { mutableStateOf(c.local) }
    var detalhe by remember { mutableStateOf(c.detalhe) }
    var inicio by remember { mutableStateOf(c.inicio) }
    var trabalho by remember { mutableStateOf(c.diasTrabalho) }
    var folga by remember { mutableStateOf(c.diasFolga) }
    var entrada by remember { mutableStateOf(c.entrada) }
    var saida by remember { mutableStateOf(c.saida) }
    var alarmeAtivo by remember { mutableStateOf(c.alarmeAtivo) }
    var alarme by remember { mutableStateOf(c.alarme) }
    var aberto by remember { mutableStateOf<Campo?>(null) }
    val contexto = LocalContext.current

    BackHandler(enabled = !primeiraVez, onBack = aoVoltar)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!primeiraVez) IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
        }

        Linha("Primeiro dia de trabalho", Formato.data(inicio)) { aberto = Campo.INICIO }
        Contador("Dias trabalhando", trabalho) { trabalho = it }
        Contador("Dias de folga", folga) { folga = it }
        Linha("Entrada", Formato.hora(entrada)) { aberto = Campo.ENTRADA }
        Linha("Saída", Formato.hora(saida)) { aberto = Campo.SAIDA }

        Cartao {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Férias", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { aberto = Campo.FERIAS }) { Text("Adicionar") }
                }
                ferias.forEach { periodo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Formato.periodo(periodo), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { ferias = ferias - periodo; aoFerias(ferias) }) {
                            Icon(Icons.Outlined.Close, "Remover")
                        }
                    }
                }
            }
        }

        Cartao {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CorDestaque.entries.forEach { opcao ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(amostraDe(opcao))
                            .then(
                                if (opcao == cor) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { cor = opcao; aoCor(opcao) },
                    )
                }
            }
        }

        Cartao {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Alarme", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = alarmeAtivo, onCheckedChange = { alarmeAtivo = it })
            }
        }
        if (alarmeAtivo) {
            Linha("Hora do alarme", Formato.hora(alarme)) { aberto = Campo.ALARME }
            if (!Agendador.podeExato(contexto) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TextButton(onClick = {
                    contexto.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}")),
                    )
                }) { Text("Permitir") }
            }
        }

        Cartao {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Alarme de saída", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = alarmeSaida, onCheckedChange = { alarmeSaida = it })
            }
        }
        if (alarmeSaida) Minutos("Minutos antes da saída", minutosAntesSaida) { minutosAntesSaida = it }
        AlarmeGarantido()

        Cartao {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Google Agenda", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = agenda, onCheckedChange = { agenda = it })
            }
        }
        if (agenda) {
            OutlinedTextField(
                value = titulo, onValueChange = { titulo = it },
                label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = detalhe, onValueChange = { detalhe = it },
                label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = local, onValueChange = { local = it },
                label = { Text("Local") }, modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                aoSalvar(
                    c.copy(
                        cor = cor, alarmeSaida = alarmeSaida, minutosAntesSaida = minutosAntesSaida, titulo = titulo, agenda = agenda, local = local, detalhe = detalhe, ferias = ferias, inicio = inicio, diasTrabalho = trabalho, diasFolga = folga,
                        entrada = entrada, saida = saida, alarmeAtivo = alarmeAtivo, alarme = alarme,
                    ),
                )
            },
            Modifier.fillMaxWidth(),
        ) { Text("Salvar") }
    }

    when (aberto) {
        Campo.INICIO -> DialogoData(inicio, { inicio = it; aberto = null }, { aberto = null })
        Campo.ENTRADA -> DialogoHora(entrada, { entrada = it; aberto = null }, { aberto = null })
        Campo.SAIDA -> DialogoHora(saida, { saida = it; aberto = null }, { aberto = null })
        Campo.ALARME -> DialogoHora(alarme, { alarme = it; aberto = null }, { aberto = null })
        Campo.FERIAS -> DialogoFerias(
            // As férias são gravadas na hora, sem precisar tocar em Salvar.
            aoOk = { periodo ->
                ferias = (ferias + periodo).distinct().sortedBy { it.inicio }
                aoFerias(ferias)
                aberto = null
            },
            aoFechar = { aberto = null },
        )
        null -> Unit
    }
}

@Composable
private fun Cartao(modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { conteudo() }
}

@Composable
private fun Linha(titulo: String, valor: String, aoClicar: () -> Unit) {
    Cartao(Modifier.clickable(onClick = aoClicar)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(valor, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Contador(titulo: String, valor: Int, aoMudar: (Int) -> Unit) {
    Cartao {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (valor > 1) aoMudar(valor - 1) }) { Icon(Icons.Outlined.Remove, "Menos") }
            Text("$valor", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { if (valor < 14) aoMudar(valor + 1) }) { Icon(Icons.Outlined.Add, "Mais") }
        }
    }
}

/** Minutos de 5 em 5, de 0 a 120. */
@Composable
private fun Minutos(titulo: String, valor: Int, aoMudar: (Int) -> Unit) {
    Cartao {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (valor > 0) aoMudar(valor - 5) }) { Icon(Icons.Outlined.Remove, "Menos") }
            Text("$valor", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { if (valor < 120) aoMudar(valor + 5) }) { Icon(Icons.Outlined.Add, "Mais") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoData(inicial: LocalDate, aoOk: (LocalDate) -> Unit, aoFechar: () -> Unit) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = aoFechar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { aoOk(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    ) { DatePicker(estado) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoFerias(aoOk: (Periodo) -> Unit, aoFechar: () -> Unit) {
    var inicio by remember { mutableStateOf(LocalDate.now()) }
    var fim by remember { mutableStateOf(LocalDate.now()) }
    // 0 = escolhendo o início, 1 = escolhendo o fim.
    var escolhendo by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Férias") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Linha("Início", Formato.data(inicio)) { escolhendo = 0 }
                Linha("Fim", Formato.data(fim)) { escolhendo = 1 }
            }
        },
        confirmButton = {
            TextButton(onClick = { aoOk(Periodo(inicio, fim)) }, enabled = fim >= inicio) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )

    when (escolhendo) {
        0 -> DialogoData(inicio, { inicio = it; if (fim < it) fim = it; escolhendo = null }, { escolhendo = null })
        1 -> DialogoData(fim, { fim = it; escolhendo = null }, { escolhendo = null })
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHora(inicial: LocalTime, aoOk: (LocalTime) -> Unit, aoFechar: () -> Unit) {
    val estado = rememberTimePickerState(inicial.hour, inicial.minute, true)
    AlertDialog(
        onDismissRequest = aoFechar,
        confirmButton = { TextButton(onClick = { aoOk(LocalTime.of(estado.hour, estado.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
        text = { TimePicker(estado) },
    )
}
