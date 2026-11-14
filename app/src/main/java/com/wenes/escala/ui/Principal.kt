package com.wenes.escala.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenes.escala.data.Config
import com.wenes.escala.data.CorDestaque
import androidx.compose.ui.text.font.FontWeight
import com.wenes.escala.data.Escala
import com.wenes.escala.data.Feriados
import com.wenes.escala.data.Formato
import com.wenes.escala.data.Repositorio
import com.wenes.escala.data.Troca
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Vermelho discreto dos feriados: aparece só como um tom de fundo, sem competir com a cor de destaque. */
private val VermelhoFeriado = Color(0xFFE25B5B)
private val AmareloFeriado = Color(0xFFFFB84D)

/** No tema vermelho o feriado vira amarelo, para não ficar igual aos dias de trabalho. */
private fun corDoFeriado(cor: CorDestaque) = if (cor == CorDestaque.VERMELHO) AmareloFeriado else VermelhoFeriado

@Composable
fun Raiz(repo: Repositorio, pedirNotificacao: () -> Unit, pedirAgenda: () -> Unit) {
    val config by repo.config.collectAsStateWithLifecycle(initialValue = null)
    val c = config ?: return
    val scope = rememberCoroutineScope()
    var ajustes by remember { mutableStateOf(false) }

    if (!c.configurado || ajustes) {
        Ajustes(
            c = c,
            primeiraVez = !c.configurado,
            aoCor = { cor -> if (c.configurado) scope.launch { repo.editar { it.copy(cor = cor) } } },
            aoFerias = { lista -> if (c.configurado) scope.launch { repo.editar { it.copy(ferias = lista) } } },
            aoSalvar = { nova ->
                scope.launch { repo.editar { atual -> nova.copy(trocas = atual.trocas) } }
                ajustes = false
                if (nova.alarmeAtivo) pedirNotificacao()
                if (nova.agenda) pedirAgenda()
            },
            aoVoltar = { ajustes = false },
        )
    } else {
        Principal(
            c = c,
            aoAjustes = { ajustes = true },
            aoTrocar = { dia, troca ->
                scope.launch {
                    repo.editar { atual ->
                        val trocas = atual.trocas.toMutableMap()
                        val igualAoPadrao = troca != null && (troca == Troca.TRABALHA) == Escala.padrao(atual, dia)
                        if (troca == null || igualAoPadrao) trocas.remove(dia) else trocas[dia] = troca
                        atual.copy(trocas = trocas)
                    }
                }
            },
        )
    }
}

@Composable
private fun Principal(c: Config, aoAjustes: () -> Unit, aoTrocar: (LocalDate, Troca?) -> Unit) {
    // O calendário é um carrossel de meses: o mês acompanha o dedo, como no Ciclo Treino.
    val pager = rememberPagerState(initialPage = PAGINA_HOJE) { TOTAL_PAGINAS }
    var diaAberto by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Escala", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = aoAjustes) { Icon(Icons.Outlined.Settings, "Ajustes") }
        }
        CartaoHoje(c)
        Calendario(c, pager, aoDia = { diaAberto = it })
        Legenda(c.cor)
        ListaFeriados(mesDaPagina(pager.currentPage))
    }

    diaAberto?.let { dia ->
        DialogoDia(c, dia, aoTrocar, aoFechar = { diaAberto = null })
    }
}

@Composable
private fun CartaoHoje(c: Config) {
    val hoje = LocalDate.now()
    val trabalha = Escala.trabalha(c, hoje)
    val cores = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            // Destaque fraco: só um tom da cor sobre o fundo. O forte (cheio) fica nos dias do calendário.
            containerColor = if (trabalha) {
                androidx.compose.ui.graphics.lerp(cores.surfaceContainerHigh, cores.primary, 0.22f)
            } else {
                cores.surfaceContainerHigh
            },
            contentColor = cores.onSurface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Hoje · ${Formato.dia(hoje)}", style = MaterialTheme.typography.labelLarge)
            Text(
                if (trabalha) "Trabalho" else "Folga",
                style = MaterialTheme.typography.displaySmall,
                color = if (trabalha) cores.primary else cores.onSurface,
            )
            if (trabalha) {
                Text(Formato.horario(c), style = MaterialTheme.typography.titleMedium)
            } else {
                Escala.proximoTrabalho(c, hoje.plusDays(1))?.let {
                    Text("Próximo trabalho: ${Formato.dia(it)}", style = MaterialTheme.typography.titleMedium)
                }
            }
            // Só aparece quando hoje é feriado.
            Feriados.nome(hoje)?.let { Text("Feriado: $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Calendario(c: Config, pager: PagerState, aoDia: (LocalDate) -> Unit) {
    val escopo = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // O título com as setas fica parado; o que desliza é o cartão do calendário inteiro, como no Ciclo Treino.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { escopo.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                Icon(Icons.Outlined.ChevronLeft, "Anterior")
            }
            Text(
                Formato.mes(mesDaPagina(pager.currentPage)),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (pager.currentPage != PAGINA_HOJE) {
                TextButton(onClick = { escopo.launch { pager.animateScrollToPage(PAGINA_HOJE) } }) { Text("Hoje") }
            }
            IconButton(onClick = { escopo.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                Icon(Icons.Outlined.ChevronRight, "Próximo")
            }
        }
        // Os meses vizinhos já ficam prontos: arrastar não monta a grade no meio do gesto.
        HorizontalPager(
            pager, Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top, beyondViewportPageCount = 1,
        ) { pagina ->
            Card(
                Modifier.fillMaxWidth().padding(4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                            Text(
                                it, Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    GradeDoMes(c, mesDaPagina(pagina), aoDia)
                }
            }
        }
    }
}

// Dez anos para trás e dez para frente, a partir do mês de hoje.
private const val PAGINA_HOJE = 120
private const val TOTAL_PAGINAS = PAGINA_HOJE * 2 + 1

private fun mesDaPagina(pagina: Int): YearMonth = YearMonth.now().plusMonths((pagina - PAGINA_HOJE).toLong())

/** Sempre 6 linhas, para o calendário não mudar de altura ao arrastar de um mês para outro. */
@Composable
private fun GradeDoMes(c: Config, mes: YearMonth, aoDia: (LocalDate) -> Unit) {
    val hoje = LocalDate.now()
    val vazios = mes.atDay(1).dayOfWeek.value % 7
    Column(Modifier.fillMaxWidth()) {
        repeat(6) { linha ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { coluna ->
                    val numero = linha * 7 + coluna - vazios + 1
                    if (numero in 1..mes.lengthOfMonth()) {
                        val dia = mes.atDay(numero)
                        Dia(Modifier.weight(1f), dia, c, dia == hoje) { aoDia(dia) }
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dia(modifier: Modifier, dia: LocalDate, c: Config, hoje: Boolean, aoClicar: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    val trabalha = Escala.trabalha(c, dia)
    val trocado = c.trocas.containsKey(dia)
    val feriado = Feriados.nome(dia) != null
    // Feriado: um tracinho embaixo do número, na cor do feriado.
    val corFeriado = corDoFeriado(c.cor)
    // Cinza só nos dias em que a pessoa trabalharia se não estivesse de férias.
    val ferias = Escala.emFerias(c, dia) && !trabalha && Escala.noCiclo(c, dia)
    Box(
        modifier.aspectRatio(1f).padding(3.dp).clip(CircleShape)
            .background(
                when {
                    trabalha -> cores.primary
                    ferias -> androidx.compose.ui.graphics.lerp(cores.surfaceContainerLow, cores.tertiary, 0.35f)
                    else -> Color.Transparent
                },
            )
            .then(if (hoje) Modifier.border(BorderStroke(2.dp, cores.onSurface), CircleShape) else Modifier)
            .clickable(onClick = aoClicar),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${dia.dayOfMonth}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (trabalha) cores.onPrimary else cores.onSurface,
        )
        if (feriado) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).size(width = 14.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(corFeriado),
            )
        }
        if (trocado) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(5.dp).clip(CircleShape)
                    .background(if (trabalha) cores.onPrimary else cores.error),
            )
        }
    }
}

@Composable
private fun Legenda(cor: CorDestaque) {
    val cores = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(
                    androidx.compose.ui.graphics.lerp(cores.surfaceContainerLow, cores.tertiary, 0.35f),
                ),
            )
            Text("Férias", style = MaterialTheme.typography.labelMedium, color = cores.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(corDoFeriado(cor)),
            )
            Text("Feriado", style = MaterialTheme.typography.labelMedium, color = cores.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(cores.primary))
            Text("Trabalho", style = MaterialTheme.typography.labelMedium, color = cores.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(cores.error))
            Text("Troca", style = MaterialTheme.typography.labelMedium, color = cores.onSurfaceVariant)
        }
    }
}

@Composable
private fun ListaFeriados(mes: YearMonth) {
    val feriados = Feriados.doMes(mes)
    if (feriados.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        feriados.forEach { (dia, nome) ->
            Text(
                "%02d/%02d · %s".format(dia.dayOfMonth, dia.monthValue, nome),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DialogoDia(c: Config, dia: LocalDate, aoTrocar: (LocalDate, Troca?) -> Unit, aoFechar: () -> Unit) {
    val trabalha = Escala.trabalha(c, dia)
    val trocado = c.trocas.containsKey(dia)
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(Formato.dia(dia)) },
        text = {
            Text(
                (if (trabalha) "Trabalho" else "Folga") + (if (trocado) " (troca)" else "") +
                    (if (Escala.emFerias(c, dia)) " (férias)" else "") +
                    (Feriados.nome(dia)?.let { "\nFeriado: $it" } ?: ""),
            )
        },
        confirmButton = {
            Row {
                if (trocado) TextButton(onClick = { aoTrocar(dia, null); aoFechar() }) { Text("Padrão") }
                TextButton(onClick = { aoTrocar(dia, Troca.TRABALHA); aoFechar() }) { Text("Trabalho") }
                TextButton(onClick = { aoTrocar(dia, Troca.FOLGA); aoFechar() }) { Text("Folga") }
            }
        },
    )
}
