package com.wenes.escala.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Marca os dias de trabalho na agenda principal do Google, como evento de dia inteiro (sem hora).
 * Só mexe nos eventos que o próprio app criou (pela marca na descrição) e só do dia de hoje em diante.
 */
object AgendaGoogle {

    private data class Existente(val id: Long, val titulo: String?, val descricao: String?, val local: String?)

    private const val TAG = "AgendaGoogle"
    private const val MARCA ="Criado pelo app Escala"
    private const val TITULO = "Trabalho"
    private const val TITULO_FERIAS = "Férias"
    private const val DIAS_A_FRENTE = 365L
    private const val MAX_DIAS_ATRAS = 1100L

    fun temPermissao(contexto: Context): Boolean =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Deixa a agenda igual à escala: cria os dias que faltam e apaga os que não são mais de trabalho.
     * [anterior] é a configuração de antes da mudança: título, descrição e local só são trocados nos eventos
     * que ainda estão como o app escreveu, para nunca apagar o que a pessoa editou à mão (como um ✅ no título).
     */
    suspend fun sincronizar(contexto: Context, c: Config, anterior: Config? = null): LocalDate? =
        withContext(Dispatchers.IO) {
            if (!temPermissao(contexto)) {
                Log.i(TAG, "Sem permissão da agenda")
                return@withContext null
            }
            runCatching { executar(contexto.contentResolver, c, anterior) }
                .onFailure { Log.e(TAG, "Falha ao sincronizar a agenda", it) }
                .getOrNull()
        }

    private fun tituloDe(c: Config) = c.titulo.trim().ifEmpty { TITULO }

    /** A descrição leva o horário e termina com a marca do app (é por ela que os eventos são reconhecidos). */
    private fun descricaoDe(c: Config) =
        listOf(c.detalhe.trim(), "Das ${Formato.hora(c.entrada)} às ${Formato.hora(c.saida)}")
            .filter { it.isNotEmpty() }.joinToString("\n") + "\n\n$MARCA"

    /** Devolve desde quando o histórico está gravado (para o app anotar), ou null se não gravou nada. */
    private fun executar(cr: ContentResolver, c: Config, anterior: Config?): LocalDate? {
        val calendario = calendarioPrincipal(cr) ?: return null
        val hoje = LocalDate.now()
        val ativo = c.agenda && c.configurado
        // O histórico começa no primeiro dia de trabalho (no máximo 3 anos para trás).
        val inicioJanela = if (ativo) minOf(c.inicio, hoje).coerceAtLeast(hoje.minusDays(MAX_DIAS_ATRAS)) else hoje
        val inicioMs = inicioJanela.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val descricao = descricaoDe(c)
        val local = c.local.trim()
        val titulo = tituloDe(c)
        val tituloAntes = anterior?.let(::tituloDe)
        val descricaoAntes = anterior?.let(::descricaoDe)
        val localAntes = anterior?.local?.trim()

        val existentes = HashMap<LocalDate, MutableList<Existente>>()
        val feriasExistentes = mutableListOf<Pair<Long, Periodo>>()
        // Pega os eventos do app que ainda não terminaram (o fim é exclusivo: o de ontem fica de fora).
        cr.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID, CalendarContract.Events.DTSTART,
                CalendarContract.Events.DESCRIPTION, CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.TITLE, CalendarContract.Events.DTEND,
            ),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DESCRIPTION} LIKE ? AND " +
                "${CalendarContract.Events.DTEND}>? AND ${CalendarContract.Events.DELETED}=0",
            arrayOf(calendario.toString(), "%$MARCA", inicioMs.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val inicio = Instant.ofEpochMilli(cursor.getLong(1)).atZone(ZoneOffset.UTC).toLocalDate()
                if (cursor.getString(4) == TITULO_FERIAS) {
                    val fim = Instant.ofEpochMilli(cursor.getLong(5)).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
                    feriasExistentes += cursor.getLong(0) to Periodo(inicio, fim)
                } else {
                    existentes.getOrPut(inicio) { mutableListOf() }
                        .add(Existente(cursor.getLong(0), cursor.getString(4), cursor.getString(2), cursor.getString(3)))
                }
            }
        }

        val desejados: Set<LocalDate> = if (ativo) {
            generateSequence(inicioJanela) { it.plusDays(1) }
                .takeWhile { it < hoje.plusDays(DIAS_A_FRENTE) }
                .filter { Escala.trabalha(c, it) }.toSet()
        } else {
            emptySet()
        }
        // As férias passadas também entram no histórico, mas nunca são apagadas pelo app.
        val feriasDesejadas: Set<Periodo> = if (ativo) c.ferias.filter { it.fim >= inicioJanela }.toSet() else emptySet()

        val operacoes = ArrayList<ContentProviderOperation>()
        val feriasVistas = HashSet<Periodo>()
        feriasExistentes.forEach { (id, periodo) ->
            val repetido = !feriasVistas.add(periodo)
            if (repetido || (periodo.fim >= hoje && periodo !in feriasDesejadas)) {
                operacoes += ContentProviderOperation.newDelete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                ).build()
            }
        }
        feriasDesejadas.filter { it !in feriasVistas }.sortedBy { it.inicio }.forEach { periodo ->
            operacoes += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValues(eventoFerias(calendario, periodo)).build()
        }
        existentes.forEach { (dia, eventos) ->
            eventos.forEachIndexed { i, (id, tituloAtual, descricaoAtual, localAtual) ->
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                // O passado é histórico: só se apaga repetido do mesmo dia, e o texto antigo (com o horário da época) fica.
                val passado = dia < hoje
                if (i > 0 || (!passado && dia !in desejados)) {
                    operacoes += ContentProviderOperation.newDelete(uri).build()
                } else if (!passado && anterior != null) {
                    // Só troca o que ainda está como o app escreveu antes: o que a pessoa editou à mão fica.
                    val mudaTitulo = tituloAtual == tituloAntes && tituloAtual != titulo
                    val mudaDescricao = descricaoAtual == descricaoAntes && descricaoAtual != descricao
                    val mudaLocal = (localAtual ?: "") == localAntes && (localAtual ?: "") != local
                    if (mudaTitulo || mudaDescricao || mudaLocal) {
                        val troca = ContentProviderOperation.newUpdate(uri)
                        if (mudaTitulo) troca.withValue(CalendarContract.Events.TITLE, titulo)
                        if (mudaDescricao) troca.withValue(CalendarContract.Events.DESCRIPTION, descricao)
                        if (mudaLocal) troca.withValue(CalendarContract.Events.EVENT_LOCATION, local)
                        operacoes += troca.build()
                    }
                }
            }
        }
        // O passado é gravado uma única vez: só dias antes do que o histórico já cobre (na 1ª vez, até ontem).
        val passadoGravadoDesde = c.historicoDesde ?: hoje
        desejados.filter { it !in existentes && (it >= hoje || it < passadoGravadoDesde) }.sorted().forEach { dia ->
            operacoes += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValues(evento(calendario, dia, titulo, descricao, local)).build()
        }
        Log.i(TAG, "Agenda $calendario: ${desejados.size} dias, ${existentes.size} já existem, ${operacoes.size} operações")
        operacoes.chunked(100).forEach { cr.applyBatch(CalendarContract.AUTHORITY, ArrayList(it)) }
        return if (ativo) minOf(passadoGravadoDesde, inicioJanela) else null
    }

    /** Um único evento de dia inteiro cobrindo todo o período de férias. */
    private fun eventoFerias(calendario: Long, periodo: Periodo) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendario)
        put(CalendarContract.Events.TITLE, TITULO_FERIAS)
        put(CalendarContract.Events.DESCRIPTION, "$TITULO_FERIAS\n\n$MARCA")
        put(CalendarContract.Events.ALL_DAY, 1)
        put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        put(CalendarContract.Events.DTSTART, periodo.inicio.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        put(CalendarContract.Events.DTEND, periodo.fim.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        put(CalendarContract.Events.HAS_ALARM, 0)
    }

    /** Evento de dia inteiro: início à meia-noite UTC e fim na meia-noite seguinte, sem horário. */
    private fun evento(calendario: Long, dia: LocalDate, titulo: String, descricao: String, local: String) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendario)
        put(CalendarContract.Events.TITLE, titulo)
        put(CalendarContract.Events.DESCRIPTION, descricao)
        put(CalendarContract.Events.EVENT_LOCATION, local)
        put(CalendarContract.Events.ALL_DAY, 1)
        put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        put(CalendarContract.Events.DTSTART, dia.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        put(CalendarContract.Events.DTEND, dia.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        put(CalendarContract.Events.HAS_ALARM, 0)
    }

    /** A agenda chamada "Trabalho" (se existir e der para escrever); senão, a principal da conta Google. */
    private fun calendarioPrincipal(cr: ContentResolver): Long? = calendarioTrabalho(cr) ?: calendarioDono(cr)

    private fun calendarioTrabalho(cr: ContentResolver): Long? =
        cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE}=? AND " +
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}=? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf("com.google", TITULO, CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun calendarioDono(cr: ContentResolver): Long? =
        cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE}=? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=? AND " +
                "${CalendarContract.Calendars.ACCOUNT_NAME}=${CalendarContract.Calendars.OWNER_ACCOUNT}",
            arrayOf("com.google", CalendarContract.Calendars.CAL_ACCESS_OWNER.toString()),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
}
