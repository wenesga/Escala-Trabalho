package com.wenes.escala.data

import java.time.LocalDate
import java.time.YearMonth
import java.util.SortedMap

/** Feriados nacionais do Brasil, calculados no aparelho (não precisa de internet). */
object Feriados {

    private val cache = HashMap<Int, Map<LocalDate, String>>()

    fun nome(dia: LocalDate): String? = doAno(dia.year)[dia]

    fun doMes(mes: YearMonth): SortedMap<LocalDate, String> =
        doAno(mes.year).filterKeys { YearMonth.from(it) == mes }.toSortedMap()

    private fun doAno(ano: Int): Map<LocalDate, String> = cache.getOrPut(ano) {
        val pascoa = pascoa(ano)
        buildMap {
            put(LocalDate.of(ano, 1, 1), "Confraternização Universal")
            put(pascoa.minusDays(48), "Carnaval")
            put(pascoa.minusDays(47), "Carnaval")
            put(pascoa.minusDays(2), "Sexta-feira Santa")
            put(LocalDate.of(ano, 4, 21), "Tiradentes")
            put(LocalDate.of(ano, 5, 1), "Dia do Trabalho")
            put(pascoa.plusDays(60), "Corpus Christi")
            put(LocalDate.of(ano, 9, 7), "Independência do Brasil")
            put(LocalDate.of(ano, 10, 12), "Nossa Senhora Aparecida")
            put(LocalDate.of(ano, 11, 2), "Finados")
            put(LocalDate.of(ano, 11, 15), "Proclamação da República")
            if (ano >= 2024) put(LocalDate.of(ano, 11, 20), "Consciência Negra")
            put(LocalDate.of(ano, 12, 25), "Natal")
        }
    }

    /** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher). */
    private fun pascoa(ano: Int): LocalDate {
        val a = ano % 19
        val b = ano / 100
        val c = ano % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mes = (h + l - 7 * m + 114) / 31
        val dia = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(ano, mes, dia)
    }
}
