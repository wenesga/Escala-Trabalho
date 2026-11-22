package com.wenes.escala.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wenes.escala.automacao.Agendador
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

private val Context.loja by preferencesDataStore("escala")

/** Guarda a escala. Toda gravação reagenda o alarme e atualiza o widget. */
class Repositorio(private val contexto: Context) {

    private object K {
        val inicio = longPreferencesKey("inicio")
        val trabalho = intPreferencesKey("dias_trabalho")
        val folga = intPreferencesKey("dias_folga")
        val entrada = intPreferencesKey("entrada")
        val saida = intPreferencesKey("saida")
        val alarmeAtivo = booleanPreferencesKey("alarme_ativo")
        val alarme = intPreferencesKey("alarme")
        val trocas = stringPreferencesKey("trocas")
        val cor = stringPreferencesKey("cor")
        val tema = stringPreferencesKey("tema")
        val fundo = stringPreferencesKey("fundo")
        val agenda = booleanPreferencesKey("agenda")
        val local = stringPreferencesKey("local")
        val detalhe = stringPreferencesKey("detalhe")
        val ferias = stringPreferencesKey("ferias")
        val alarmeSaida = booleanPreferencesKey("alarme_saida")
        val titulo = stringPreferencesKey("titulo")
        val historico = longPreferencesKey("historico_desde")
        val minutosAntesSaida = intPreferencesKey("minutos_antes_saida")
        val compensarTroca = booleanPreferencesKey("compensar_troca")
    }

    val config: Flow<Config> = contexto.loja.data.map { p ->
        val padrao = Config()
        Config(
            configurado = p[K.inicio] != null,
            inicio = p[K.inicio]?.let(LocalDate::ofEpochDay) ?: padrao.inicio,
            diasTrabalho = p[K.trabalho] ?: padrao.diasTrabalho,
            diasFolga = p[K.folga] ?: padrao.diasFolga,
            entrada = p[K.entrada]?.let(::minutosParaHora) ?: padrao.entrada,
            saida = p[K.saida]?.let(::minutosParaHora) ?: padrao.saida,
            alarmeAtivo = p[K.alarmeAtivo] ?: padrao.alarmeAtivo,
            alarme = p[K.alarme]?.let(::minutosParaHora) ?: padrao.alarme,
            trocas = lerTrocas(p[K.trocas]),
            agenda = p[K.agenda] ?: padrao.agenda,
            local = p[K.local] ?: padrao.local,
            detalhe = p[K.detalhe] ?: padrao.detalhe,
            ferias = lerFerias(p[K.ferias]),
            alarmeSaida = p[K.alarmeSaida] ?: padrao.alarmeSaida,
            titulo = p[K.titulo] ?: padrao.titulo,
            historicoDesde = p[K.historico]?.let(LocalDate::ofEpochDay),
            minutosAntesSaida = p[K.minutosAntesSaida] ?: padrao.minutosAntesSaida,
            compensarTroca = p[K.compensarTroca] ?: padrao.compensarTroca,
            cor = p[K.cor]?.let { n -> CorDestaque.entries.firstOrNull { it.name == n } } ?: padrao.cor,
            tema = p[K.tema]?.let { n -> TemaApp.entries.firstOrNull { it.name == n } } ?: padrao.tema,
            fundo = p[K.fundo]?.let { n -> FundoEscuro.entries.firstOrNull { it.name == n } } ?: padrao.fundo,
        )
    }

    suspend fun atual(): Config = config.first()

    suspend fun editar(mudar: (Config) -> Config) {
        val antes = atual()
        val nova = mudar(antes).copy(configurado = true)
        contexto.loja.edit { p ->
            p[K.inicio] = nova.inicio.toEpochDay()
            p[K.trabalho] = nova.diasTrabalho
            p[K.folga] = nova.diasFolga
            p[K.entrada] = horaParaMinutos(nova.entrada)
            p[K.saida] = horaParaMinutos(nova.saida)
            p[K.cor] = nova.cor.name
            p[K.tema] = nova.tema.name
            p[K.fundo] = nova.fundo.name
            p[K.agenda] = nova.agenda
            p[K.alarmeSaida] = nova.alarmeSaida
            p[K.titulo] = nova.titulo
            nova.historicoDesde?.let { p[K.historico] = it.toEpochDay() }
            p[K.minutosAntesSaida] = nova.minutosAntesSaida
            p[K.compensarTroca] = nova.compensarTroca
            p[K.local] = nova.local
            p[K.detalhe] = nova.detalhe
            p[K.ferias] = nova.ferias.joinToString(";") { "${it.inicio.toEpochDay()}:${it.fim.toEpochDay()}" }
            p[K.alarmeAtivo] = nova.alarmeAtivo
            p[K.alarme] = horaParaMinutos(nova.alarme)
            p[K.trocas] = nova.trocas.entries.joinToString(";") { "${it.key.toEpochDay()}:${it.value.name}" }
        }
        Agendador.reagendar(contexto, nova, antes)
    }

    /** Anota até onde o histórico já foi gravado na agenda (não reagenda nada). */
    suspend fun marcarHistorico(desde: LocalDate) {
        contexto.loja.edit { it[K.historico] = desde.toEpochDay() }
    }

    private fun horaParaMinutos(t: LocalTime) = t.hour * 60 + t.minute
    private fun minutosParaHora(m: Int): LocalTime = LocalTime.of(m / 60, m % 60)

    private fun lerFerias(texto: String?): List<Periodo> =
        texto.orEmpty().split(';').mapNotNull { par ->
            val partes = par.split(':')
            if (partes.size != 2) return@mapNotNull null
            runCatching { Periodo(LocalDate.ofEpochDay(partes[0].toLong()), LocalDate.ofEpochDay(partes[1].toLong())) }
                .getOrNull()
        }.sortedBy { it.inicio }

    private fun lerTrocas(texto: String?): Map<LocalDate, Troca> =
        texto.orEmpty().split(';').mapNotNull { par ->
            val partes = par.split(':')
            if (partes.size != 2) return@mapNotNull null
            runCatching { LocalDate.ofEpochDay(partes[0].toLong()) to Troca.valueOf(partes[1]) }.getOrNull()
        }.toMap()
}
