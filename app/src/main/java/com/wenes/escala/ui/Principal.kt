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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenes.escala.data.Config
import com.wenes.escala.data.CorDestaque
import com.wenes.escala.data.DIAS_ATE_COMPENSACAO
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

/**
 * Dia extra (cobriu um colega): cada cor de destaque tem a sua cor de extra, uma vizinha dela no círculo de cores,
 * para não parecer um dia de trabalho comum. No verde, por exemplo, é o turquesa (o verde puxado para o azul).
 */
private fun corDoExtra(cor: CorDestaque) = when (cor) {
    CorDestaque.VERDE -> Color(0xFF20A398)
    CorDestaque.AZUL -> Color(0xFF6B5CE7)
    CorDestaque.ROXO -> Color(0xFFD05BC8)
    CorDestaque.TURQUESA -> Color(0xFF3D7FEF)
    CorDestaque.LARANJA -> Color(0xFFE0B03A)
    CorDestaque.VERMELHO -> Color(0xFFC94F8E)
    CorDestaque.CINZA -> Color(0xFF7F9BB8)
}

/** Número legível sobre a cor do extra: escuro nas cores claras, branco nas escuras. */
private fun textoSobre(fundo: Color) =
    if (fundo.luminance() > 0.45f) Color(0xFF1B1C1E) else Color.White

/** No tema vermelho o feriado vira amarelo, para não ficar igual aos dias de trabalho. */
private fun corDoFeriado(cor: CorDestaque) = if (cor == CorDestaque.VERMELHO) AmareloFeriado else VermelhoFeriado

@Composable
fun Raiz(repo: Repositorio, pedirNotificacao: () -> Unit, pedirAgenda: () -> Unit) {
    val config by repo.config.collectAsStateWithLifecycle(initialValue = null)
    val c = config ?: return
    val scope = rememberCoroutineScope()
    var ajustes by remember { mutableStateOf(false) }

    if (!c.configurado) {
        TelaPrimeiraVez(c) { nova ->
            scope.launch { repo.editar { atual -> nova.copy(trocas = atual.trocas) } }
            if (nova.alarmeAtivo) pedirNotificacao()
        }
    } else {
        // Ajustes é uma folha que sobe de baixo para cima e some arrastando para baixo.
        // As mudanças valem na hora: cada uma grava, refaz o alarme, o widget e a agenda.
        if (ajustes) {
            FolhaAjustes(
                c = c,
                aoMudar = { mudar -> scope.launch { repo.editar(mudar) } },
                pedirNotificacao = pedirNotificacao,
                pedirAgenda = pedirAgenda,
                aoFechar = { ajustes = false },
            )
        }
        Principal(
            c = c,
            aoAjustes = { ajustes = true },
            aoTrocar = { dia, troca ->
                scope.launch {
                    repo.editar { atual ->
                        val trocas = atual.trocas.toMutableMap()
                        // Só os extras ficam gravados; as folgas de compensação são calculadas a partir deles.
                        // Por isso marcar e desmarcar é sempre reversível e uma marcação nunca apaga outra.
                        val extraDaFolga = Escala.compensacoes(atual)[dia]
                        when {
                            // "Padrão" numa folga de compensação desfaz a troca inteira: o extra dela vai junto.
                            extraDaFolga != null && troca == null -> trocas.remove(extraDaFolga)
                            // "Trabalho" nela só liga o dia: o extra fica e a folga passa para o próximo dia livre.
                            extraDaFolga != null && troca == Troca.TRABALHA -> trocas[dia] = Troca.TRABALHA
                            extraDaFolga != null -> Unit
                            troca == null || (troca == Troca.TRABALHA) == Escala.padrao(atual, dia) -> trocas.remove(dia)
                            else -> trocas[dia] = troca
                        }
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
        AvisoDePermissao(alarmeLigado = c.alarmeAtivo || c.alarmeSaida)
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
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, lineHeight = 38.sp),
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
            Feriados.nome(hoje)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
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
    // Extra: dia de folga em que a pessoa cobriu um colega. Fica no tom escuro da cor de destaque.
    val extra = trocado && trabalha && !Escala.padrao(c, dia)
    val compensacao = Escala.compensacoes(c).containsKey(dia)
    Box(
        modifier.aspectRatio(1f).padding(3.dp).clip(CircleShape)
            .background(
                when {
                    trabalha -> if (extra) corDoExtra(c.cor) else cores.primary
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
            color = when {
                extra -> textoSobre(corDoExtra(c.cor))
                trabalha -> cores.onPrimary
                else -> cores.onSurface
            },
        )
        if (feriado) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).size(width = 14.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(corFeriado),
            )
        }
        // Folga que veio de uma troca (a compensação): um pontinho. O extra já se vê pela cor.
        if ((trocado || compensacao) && !trabalha) {
            Box(
                // Embaixo do número; se o dia também é feriado, um pouco acima do tracinho.
                Modifier.align(Alignment.BottomCenter).padding(bottom = if (feriado) 12.dp else 6.dp).size(6.dp)
                    .clip(CircleShape).background(corDoExtra(c.cor)),
            )
        }
    }
}

@Composable
private fun Legenda(cor: CorDestaque) {
    val cores = MaterialTheme.colorScheme
    @Composable
    fun item(nome: String, marca: @Composable () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            marca()
            Text(nome, style = MaterialTheme.typography.labelMedium, color = cores.onSurfaceVariant)
        }
    }
    val centro = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = centro) {
            item("Trabalho") { Box(Modifier.size(14.dp).clip(CircleShape).background(cores.primary)) }
            item("Extra") { Box(Modifier.size(14.dp).clip(CircleShape).background(corDoExtra(cor))) }
            item("Troca") { Box(Modifier.size(7.dp).clip(CircleShape).background(corDoExtra(cor))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = centro) {
            item("Férias") {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        androidx.compose.ui.graphics.lerp(cores.surfaceContainerLow, cores.tertiary, 0.35f),
                    ),
                )
            }
            item("Feriado") {
                Box(
                    Modifier.size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(2.dp))
                        .background(corDoFeriado(cor)),
                )
            }
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
    val trocado = c.trocas.containsKey(dia) || Escala.compensacoes(c).containsKey(dia)
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
