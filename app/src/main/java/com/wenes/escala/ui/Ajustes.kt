package com.wenes.escala.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wenes.escala.data.Config
import com.wenes.escala.data.CorDestaque
import com.wenes.escala.data.Formato
import com.wenes.escala.data.Periodo
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** As páginas de Ajustes, como no Ciclo Treino: uma lista de categorias, cada uma abre a sua página. */
private enum class Pagina(val titulo: String, val icone: ImageVector) {
    ESCALA("Escala", Icons.Outlined.EventRepeat),
    HORARIO("Horário", Icons.Outlined.Schedule),
    ALARME("Alarme", Icons.Outlined.Alarm),
    FERIAS("Férias", Icons.Outlined.BeachAccess),
    AGENDA("Google Agenda", Icons.Outlined.EditCalendar),
    APARENCIA("Aparência", Icons.Outlined.Palette),
}

private fun resumo(pagina: Pagina, c: Config): String = when (pagina) {
    Pagina.ESCALA -> "Trabalha ${c.diasTrabalho}, folga ${c.diasFolga} · desde ${Formato.data(c.inicio)}"
    Pagina.HORARIO -> Formato.horario(c)
    Pagina.ALARME -> listOf(
        if (c.alarmeAtivo) "Entrada ${Formato.hora(c.alarme)}" else "Entrada desligada",
        if (c.alarmeSaida) "saída ${c.minutosAntesSaida} min antes" else "saída desligada",
    ).joinToString(" · ")
    Pagina.FERIAS -> when (c.ferias.size) {
        0 -> "Nenhuma"
        1 -> Formato.periodo(c.ferias.first())
        else -> "${c.ferias.size} períodos"
    }
    Pagina.AGENDA -> if (c.agenda) "Ligado · ${c.titulo}" else "Desligado"
    Pagina.APARENCIA -> nomeDaCor(c.cor)
}

private fun nomeDaCor(cor: CorDestaque) = when (cor) {
    CorDestaque.AZUL -> "Azul"
    CorDestaque.ROXO -> "Roxo"
    CorDestaque.TURQUESA -> "Turquesa"
    CorDestaque.LARANJA -> "Laranja"
    CorDestaque.VERMELHO -> "Vermelho"
    CorDestaque.VERDE -> "Verde"
    CorDestaque.CINZA -> "Cinza"
}

/**
 * Ajustes como uma folha que sobe de baixo para cima e some arrastando para baixo. Dentro dela, a lista de
 * categorias; cada uma abre a sua página curta, então não há uma tela comprida para rolar.
 * As mudanças valem na hora, sem botão Salvar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaAjustes(
    c: Config,
    aoMudar: ((Config) -> Config) -> Unit,
    pedirNotificacao: () -> Unit,
    pedirAgenda: () -> Unit,
    aoFechar: () -> Unit,
) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // A folha inteira tem a cor do fundo do calendário (mais clara que a tela, senão se mistura com ela),
    // e as linhas ficam direto sobre ela, sem caixa por dentro.
    ModalBottomSheet(
        onDismissRequest = aoFechar,
        sheetState = estado,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        CompositionLocalProvider(LocalCorCartao provides Color.Transparent) {
            ConteudoAjustes(c, aoMudar, pedirNotificacao, pedirAgenda, aoFechar)
        }
    }
}

/** Cor dos cartões das linhas de Ajustes: na folha, um tom acima dela; fora dela, o padrão. */
internal val LocalCorCartao = compositionLocalOf<Color?> { null }

@Composable
private fun ConteudoAjustes(
    c: Config,
    aoMudar: ((Config) -> Config) -> Unit,
    pedirNotificacao: () -> Unit,
    pedirAgenda: () -> Unit,
    aoVoltar: () -> Unit,
) {
    var nome by rememberSaveable { mutableStateOf<String?>(null) }
    val pagina = nome?.let { Pagina.valueOf(it) }
    // Dentro de uma página, voltar leva à lista de categorias; na lista, fecha a folha.
    BackHandler(enabled = pagina != null) { nome = null }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pagina != null) {
                IconButton(onClick = { nome = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            }
            Text(pagina?.titulo ?: "Ajustes", style = MaterialTheme.typography.headlineSmall)
        }
        when (pagina) {
            null -> Categorias(c) { nome = it.name }
            Pagina.ESCALA -> PaginaEscala(c, aoMudar)
            Pagina.HORARIO -> PaginaHorario(c, aoMudar)
            Pagina.ALARME -> PaginaAlarme(c, aoMudar, pedirNotificacao)
            Pagina.FERIAS -> PaginaFerias(c, aoMudar)
            Pagina.AGENDA -> PaginaAgenda(c, aoMudar, pedirAgenda)
            Pagina.APARENCIA -> PaginaAparencia(c, aoMudar)
        }
    }
}

@Composable
private fun Categorias(c: Config, aoAbrir: (Pagina) -> Unit) {
    // Um cartãozinho por categoria, discreto: só um tom acima da folha, com pouco espaço entre eles.
    val cores = MaterialTheme.colorScheme
    val escuro = cores.background.luminance() < 0.5f
    val corDoCartinho = if (escuro) cores.surfaceContainerHigh else cores.surfaceContainer
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pagina.entries.forEach { pagina ->
            Card(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { aoAbrir(pagina) },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = corDoCartinho),
            ) {
                ListItem(
                    headlineContent = { Text(pagina.titulo) },
                    supportingContent = { Text(resumo(pagina, c)) },
                    leadingContent = { Icon(pagina.icone, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun PaginaEscala(c: Config, aoMudar: ((Config) -> Config) -> Unit) {
    var data by remember { mutableStateOf(false) }
    Linha("Primeiro dia de trabalho", Formato.data(c.inicio)) { data = true }
    Contador("Dias trabalhando", c.diasTrabalho) { v -> aoMudar { it.copy(diasTrabalho = v) } }
    Contador("Dias de folga", c.diasFolga) { v -> aoMudar { it.copy(diasFolga = v) } }
    Interruptor("Compensar troca", c.compensarTroca) { v -> aoMudar { it.copy(compensarTroca = v) } }
    if (data) DialogoData(c.inicio, { d -> aoMudar { it.copy(inicio = d) }; data = false }, { data = false })
}

@Composable
private fun PaginaHorario(c: Config, aoMudar: ((Config) -> Config) -> Unit) {
    var campo by remember { mutableStateOf<String?>(null) }
    Linha("Entrada", Formato.hora(c.entrada)) { campo = "entrada" }
    Linha("Saída", Formato.hora(c.saida)) { campo = "saida" }
    when (campo) {
        "entrada" -> DialogoHora(c.entrada, { h -> aoMudar { it.copy(entrada = h) }; campo = null }, { campo = null })
        "saida" -> DialogoHora(c.saida, { h -> aoMudar { it.copy(saida = h) }; campo = null }, { campo = null })
    }
}

@Composable
private fun PaginaAlarme(c: Config, aoMudar: ((Config) -> Config) -> Unit, pedirNotificacao: () -> Unit) {
    var hora by remember { mutableStateOf(false) }
    Interruptor("Alarme", c.alarmeAtivo) { v ->
        aoMudar { it.copy(alarmeAtivo = v) }
        if (v) pedirNotificacao()
    }
    if (c.alarmeAtivo) Linha("Hora do alarme", Formato.hora(c.alarme)) { hora = true }
    Interruptor("Alarme de saída", c.alarmeSaida) { v -> aoMudar { it.copy(alarmeSaida = v) } }
    if (c.alarmeSaida) {
        Minutos("Minutos antes da saída", c.minutosAntesSaida) { v -> aoMudar { it.copy(minutosAntesSaida = v) } }
    }
    AlarmeGarantido()
    if (hora) DialogoHora(c.alarme, { h -> aoMudar { it.copy(alarme = h) }; hora = false }, { hora = false })
}

@Composable
private fun PaginaFerias(c: Config, aoMudar: ((Config) -> Config) -> Unit) {
    var adicionando by remember { mutableStateOf(false) }
    Cartao {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Períodos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { adicionando = true }) { Text("Adicionar") }
            }
            if (c.ferias.isEmpty()) {
                Text(
                    "Nenhuma férias cadastrada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            c.ferias.forEach { periodo ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Formato.periodo(periodo), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { aoMudar { it.copy(ferias = it.ferias - periodo) } }) {
                        Icon(Icons.Outlined.Close, "Remover")
                    }
                }
            }
        }
    }
    if (adicionando) {
        DialogoFerias(
            aoOk = { periodo ->
                aoMudar { it.copy(ferias = (it.ferias + periodo).distinct().sortedBy { p -> p.inicio }) }
                adicionando = false
            },
            aoFechar = { adicionando = false },
        )
    }
}

@Composable
private fun PaginaAgenda(c: Config, aoMudar: ((Config) -> Config) -> Unit, pedirAgenda: () -> Unit) {
    Interruptor("Google Agenda", c.agenda) { v ->
        aoMudar { it.copy(agenda = v) }
        if (v) pedirAgenda()
    }
    if (c.agenda) {
        CampoTexto("Título", c.titulo, umaLinha = true) { t -> aoMudar { it.copy(titulo = t) } }
        CampoTexto("Descrição", c.detalhe, umaLinha = false) { t -> aoMudar { it.copy(detalhe = t) } }
        CampoTexto("Local", c.local, umaLinha = false) { t -> aoMudar { it.copy(local = t) } }
    }
}

@Composable
private fun PaginaAparencia(c: Config, aoMudar: ((Config) -> Config) -> Unit) {
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
                            if (opcao == c.cor) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { aoMudar { it.copy(cor = opcao) } },
                )
            }
        }
    }
}

/** A primeira vez: só o essencial; o resto fica com os valores padrão e se ajusta depois. */
@Composable
fun TelaPrimeiraVez(c: Config, aoComecar: (Config) -> Unit) {
    var inicio by remember { mutableStateOf(c.inicio) }
    var trabalho by remember { mutableStateOf(c.diasTrabalho) }
    var folga by remember { mutableStateOf(c.diasFolga) }
    var entrada by remember { mutableStateOf(c.entrada) }
    var saida by remember { mutableStateOf(c.saida) }
    var campo by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Escala", style = MaterialTheme.typography.headlineSmall)
        Linha("Primeiro dia de trabalho", Formato.data(inicio)) { campo = "inicio" }
        Contador("Dias trabalhando", trabalho) { trabalho = it }
        Contador("Dias de folga", folga) { folga = it }
        Linha("Entrada", Formato.hora(entrada)) { campo = "entrada" }
        Linha("Saída", Formato.hora(saida)) { campo = "saida" }
        Button(
            onClick = {
                aoComecar(c.copy(inicio = inicio, diasTrabalho = trabalho, diasFolga = folga, entrada = entrada, saida = saida))
            },
            Modifier.fillMaxWidth(),
        ) { Text("Começar") }
    }
    when (campo) {
        "inicio" -> DialogoData(inicio, { inicio = it; campo = null }, { campo = null })
        "entrada" -> DialogoHora(entrada, { entrada = it; campo = null }, { campo = null })
        "saida" -> DialogoHora(saida, { saida = it; campo = null }, { campo = null })
    }
}

@Composable
private fun Cartao(modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = LocalCorCartao.current ?: MaterialTheme.colorScheme.surfaceContainerLow,
        ),
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
private fun Interruptor(titulo: String, ligado: Boolean, aoMudar: (Boolean) -> Unit) {
    Cartao {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = ligado, onCheckedChange = aoMudar)
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

/**
 * Campo de texto que grava depois de uns instantes sem digitar (e ao sair da página), para não gravar a cada letra:
 * cada gravação refaz a agenda.
 */
@Composable
private fun CampoTexto(rotulo: String, valor: String, umaLinha: Boolean, aoConfirmar: (String) -> Unit) {
    var texto by remember { mutableStateOf(valor) }
    val confirmar by rememberUpdatedState(aoConfirmar)
    val valorAtual by rememberUpdatedState(valor)
    val textoAtual by rememberUpdatedState(texto)
    LaunchedEffect(texto) {
        delay(800)
        if (texto != valorAtual) confirmar(texto)
    }
    DisposableEffect(Unit) {
        onDispose { if (textoAtual != valorAtual) confirmar(textoAtual) }
    }
    OutlinedTextField(
        value = texto, onValueChange = { texto = it },
        label = { Text(rotulo) }, singleLine = umaLinha, modifier = Modifier.fillMaxWidth(),
    )
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
