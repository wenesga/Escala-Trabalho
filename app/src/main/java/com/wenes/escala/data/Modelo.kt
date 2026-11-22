package com.wenes.escala.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Troca pontual de um dia: a escala base continua igual, só aquele dia muda. */
enum class Troca { TRABALHA, FOLGA }

/** Período de férias, do primeiro ao último dia (os dois incluídos). */
data class Periodo(val inicio: LocalDate, val fim: LocalDate)

/** Ordem igual ao Organizador Pro: Automática (Android 12+) primeiro. */
enum class CorDestaque { AUTOMATICA, AZUL, VERDE, ROXO, TURQUESA, LARANJA, ROSA }

/** Tema do app: segue o sistema ou fica fixo. */
enum class TemaApp { SISTEMA, CLARO, ESCURO }

/** Fundos do tema escuro (cor em ARGB). */
enum class FundoEscuro(val argb: Long, val nome: String) {
    PRETO(0xFF000000, "Preto"), PETROLEO(0xFF22272B, "Petróleo"), MARINHO(0xFF171A26, "Marinho"),
    VINHO(0xFF1A1113, "Vinho"), GRAFITE(0xFF121416, "Grafite"), VERDE(0xFF0B1512, "Verde"),
    ROXO(0xFF14101D, "Roxo"),
}

data class Config(
    val cor: CorDestaque = CorDestaque.TURQUESA,
    val tema: TemaApp = TemaApp.SISTEMA,
    val fundo: FundoEscuro = FundoEscuro.MARINHO,
    /** Marca os dias de trabalho no Google Agenda, como evento de dia inteiro. */
    val agenda: Boolean = false,
    /** Título dos eventos de trabalho no Google Agenda. */
    val titulo: String = "Trabalho",
    /** Endereço do local de trabalho, gravado no campo Local do evento. */
    val local: String = "UBS Parque das Colinas, R. 7-A, 00 - Arraias, TO, 77330-000, Brasil",
    /** Texto da descrição do evento, acima do horário. */
    val detalhe: String = "Trabalho no posto de saúde",
    /** Períodos de férias: nesses dias não há trabalho, alarme nem evento de trabalho na agenda. */
    val ferias: List<Periodo> = emptyList(),
    /** Desde quando o histórico já foi gravado na agenda. O passado só é gravado uma vez, nunca regravado. */
    val historicoDesde: LocalDate? = null,
    val configurado: Boolean = false,
    /** Qualquer dia em que a pessoa trabalha: o ciclo se repete a partir dele. */
    val inicio: LocalDate = LocalDate.now(),
    val diasTrabalho: Int = 1,
    val diasFolga: Int = 1,
    val entrada: LocalTime = LocalTime.of(20, 0),
    val saida: LocalTime = LocalTime.of(6, 0),
    val alarmeAtivo: Boolean = true,
    /** Segundo alarme, na hora da saída (o fim do turno). */
    val alarmeSaida: Boolean = true,
    /** Ao cobrir o dia de um colega, marca sozinho a folga de compensação, [DIAS_ATE_COMPENSACAO] dias depois. */
    val compensarTroca: Boolean = true,
    /** O alarme de saída toca tantos minutos antes do fim do turno (a hora da Saída). */
    val minutosAntesSaida: Int = 30,
    val alarme: LocalTime = LocalTime.of(17, 0),
    val trocas: Map<LocalDate, Troca> = emptyMap(),
)

/**
 * Quem pede é sempre o colega: a pessoa cobre o dia dele e, no ritmo de dia sim, dia não, o colega cobre
 * o dia dela 3 dias depois (cobre dia 16, o colega cobre o 19).
 */
const val DIAS_ATE_COMPENSACAO = 3L

object Escala {

    fun emFerias(c: Config, dia: LocalDate): Boolean = c.ferias.any { dia >= it.inicio && dia <= it.fim }

    /** O dia, pela escala base (sem trocas), já sem os dias de férias. */
    fun padrao(c: Config, dia: LocalDate): Boolean = !emFerias(c, dia) && noCiclo(c, dia)

    /** O dia pelo ritmo da escala (dias de trabalho e de folga), sem olhar as férias. */
    fun noCiclo(c: Config, dia: LocalDate): Boolean {
        val ciclo = (c.diasTrabalho + c.diasFolga).toLong()
        val posicao = Math.floorMod(dia.toEpochDay() - c.inicio.toEpochDay(), ciclo)
        return posicao < c.diasTrabalho
    }

    fun trabalha(c: Config, dia: LocalDate): Boolean = when (c.trocas[dia]) {
        Troca.TRABALHA -> true
        Troca.FOLGA -> false
        null -> padrao(c, dia) && !compensacoes(c).containsKey(dia)
    }

    private class Guardado(
        val trocas: Map<LocalDate, Troca>, val inicio: LocalDate, val trabalho: Int, val folga: Int,
        val ferias: List<Periodo>, val resultado: Map<LocalDate, LocalDate>,
    )

    @Volatile
    private var guardado: Guardado? = null

    /**
     * Folgas de compensação, calculadas na hora a partir dos extras (nada disso fica gravado): cada extra
     * gera uma folga 3 dias depois ou, se esse dia já foi marcado, no próximo dia de trabalho livre.
     * Devolve dia de folga -> dia extra que a gerou. Marcar ou desmarcar um extra só refaz a conta.
     */
    fun compensacoes(c: Config): Map<LocalDate, LocalDate> {
        if (!c.compensarTroca) return emptyMap()
        guardado?.let {
            if (it.trocas === c.trocas && it.inicio == c.inicio && it.trabalho == c.diasTrabalho &&
                it.folga == c.diasFolga && it.ferias == c.ferias
            ) return it.resultado
        }
        val extras = c.trocas.filter { (d, t) -> t == Troca.TRABALHA && !padrao(c, d) }.keys.sorted()
        val folgas = mutableMapOf<LocalDate, LocalDate>()
        for (extra in extras) {
            val dia = generateSequence(extra.plusDays(DIAS_ATE_COMPENSACAO)) { it.plusDays(1) }.take(800)
                .firstOrNull { padrao(c, it) && !c.trocas.containsKey(it) && !folgas.containsKey(it) } ?: continue
            folgas[dia] = extra
        }
        guardado = Guardado(c.trocas, c.inicio, c.diasTrabalho, c.diasFolga, c.ferias, folgas)
        return folgas
    }

    fun proximoTrabalho(c: Config, desde: LocalDate): LocalDate? =
        (0L..800L).map { desde.plusDays(it) }.firstOrNull { trabalha(c, it) }
}

object Formato {
    private val semana = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    private val meses = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )
    private val hora = DateTimeFormatter.ofPattern("HH:mm")
    private val data = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun hora(t: LocalTime): String = t.format(hora)
    fun data(d: LocalDate): String = d.format(data)
    /** "01/07 – 30/07/2026". */
    fun periodo(p: Periodo): String =
        "%02d/%02d – %s".format(p.inicio.dayOfMonth, p.inicio.monthValue, data(p.fim))

    fun horario(c: Config): String = "${hora(c.entrada)} às ${hora(c.saida)}"

    /** "Qui, 13/11". */
    fun dia(d: LocalDate): String =
        "${semana[d.dayOfWeek.value % 7]}, %02d/%02d".format(d.dayOfMonth, d.monthValue)

    /** "Novembro 2026". */
    fun mes(m: YearMonth): String = "${meses[m.monthValue - 1]} ${m.year}"
}
