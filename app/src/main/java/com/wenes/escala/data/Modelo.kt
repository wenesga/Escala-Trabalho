package com.wenes.escala.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Troca pontual de um dia: a escala base continua igual, só aquele dia muda. */
enum class Troca { TRABALHA, FOLGA }

/** Período de férias, do primeiro ao último dia (os dois incluídos). */
data class Periodo(val inicio: LocalDate, val fim: LocalDate)

enum class CorDestaque { AZUL, ROXO, TURQUESA, LARANJA, VERMELHO, VERDE, CINZA }

data class Config(
    val cor: CorDestaque = CorDestaque.AZUL,
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
        null -> padrao(c, dia)
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
