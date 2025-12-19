package no.nav.hag.utils.bakgrunnsjobb

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.readRawBytes
import io.prometheus.client.Counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.hag.utils.bakgrunnsjobb.processing.AutoCleanJobbProcessor
import no.nav.hag.utils.bakgrunnsjobb.processing.AutoCleanJobbProcessor.Companion.JOB_TYPE
import java.time.LocalDateTime

private val om =
    ObjectMapper().apply {
        registerKotlinModule()
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        dateFormat = StdDateFormat()
    }

class BakgrunnsjobbService(
    val bakgrunnsjobbRepository: BakgrunnsjobbRepository,
    delayMillis: Long = 30 * 1000L,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    val bakgrunnsvarsler: Bakgrunnsvarsler = TomVarsler(),
) : RecurringJob(coroutineScope, delayMillis) {
    val prossesserere = HashMap<String, BakgrunnsjobbProsesserer>()

    fun startAutoClean(
        frekvensITimer: Int,
        slettEldreEnnMaaneder: Long,
    ) {
        if (frekvensITimer < 1 || slettEldreEnnMaaneder < 0) {
            logger.info("startautoclean forsøkt startet med ugyldige parametre.")
            throw IllegalArgumentException("start autoclean må ha en frekvens større enn 1 og slettEldreEnnMaander større enn 0")
        }
        if (isRunning) {
            val autocleanjobber = bakgrunnsjobbRepository.findAutoCleanJobs()

            if (autocleanjobber.isEmpty()) {
                val data =
                    om.writeValueAsString(
                        AutoCleanJobbProcessor.JobbData(
                            slettEldre = slettEldreEnnMaaneder,
                            interval = frekvensITimer,
                        ),
                    )

                bakgrunnsjobbRepository.save(
                    Bakgrunnsjobb(
                        kjoeretid = LocalDateTime.now().plusHours(frekvensITimer.toLong()),
                        maksAntallForsoek = 10,
                        data = data,
                        type = JOB_TYPE,
                    ),
                )
            } else {
                val ekisterendeAutoCleanJobb = autocleanjobber[0]
                bakgrunnsjobbRepository.delete(ekisterendeAutoCleanJobb.uuid)
                startAutoClean(frekvensITimer, slettEldreEnnMaaneder)
            }
        } else {
            logger.warn("BakgrunnsjobbService er stoppet, kjører ikke autoclean!")
        }
    }

    fun registrer(prosesserer: BakgrunnsjobbProsesserer) {
        prossesserere[prosesserer.type] = prosesserer
    }

    inline fun <reified T : BakgrunnsjobbProsesserer> opprettJobb(
        kjoeretid: LocalDateTime = LocalDateTime.now(),
        forsoek: Int = 0,
        maksAntallForsoek: Int = 3,
        data: String,
    ) {
        val prosesserer =
            prossesserere.values.filterIsInstance<T>().firstOrNull()
                ?: throw IllegalArgumentException("Denne prosessereren er ukjent")

        bakgrunnsjobbRepository.save(
            Bakgrunnsjobb(
                type = prosesserer.type,
                kjoeretid = kjoeretid,
                forsoek = forsoek,
                maksAntallForsoek = maksAntallForsoek,
                data = data,
            ),
        )
    }

    inline fun <reified T : BakgrunnsjobbProsesserer> opprettJobbJson(
        kjoeretid: LocalDateTime = LocalDateTime.now(),
        forsoek: Int = 0,
        maksAntallForsoek: Int = 3,
        data: JsonElement,
    ) {
        val prosesserer =
            prossesserere.values.filterIsInstance<T>().firstOrNull()
                ?: throw IllegalArgumentException("Denne prosessereren er ukjent")

        bakgrunnsjobbRepository.save(
            Bakgrunnsjobb(
                type = prosesserer.type,
                kjoeretid = kjoeretid,
                forsoek = forsoek,
                maksAntallForsoek = maksAntallForsoek,
                dataJson = data,
            ),
        )
    }

    override fun doJob() {
        do {
            val wasEmpty =
                finnVentende()
                    .also { logger.debug("Fant ${it.size} bakgrunnsjobber å kjøre") }
                    .onEach { prosesser(it) }
                    .isEmpty()
        } while (!wasEmpty)
    }

    fun prosesser(jobb: Bakgrunnsjobb) {
        val nyBehandlet = LocalDateTime.now()
        val nyttForsoek = jobb.forsoek + 1

        val prossessorForType =
            prossesserere[jobb.type]
                ?: throw IllegalArgumentException("Det finnes ingen prossessor for typen '${jobb.type}'. Dette må konfigureres.")

        val nesteKjoeretid = prossessorForType.nesteForsoek(nyttForsoek, LocalDateTime.now())

        var nyStatus = jobb.status

        try {
            prossessorForType.prosesser(jobb)
            nyStatus = Bakgrunnsjobb.Status.OK
            OK_JOBB_COUNTER.labels(jobb.type).inc()
        } catch (ex: Throwable) {
            val responseBody = tryGetResponseBody(ex)
            val responseBodyMessage = responseBody?.let { "Feil fra ekstern tjeneste: $it" }.orEmpty()

            nyStatus = if (nyttForsoek >= jobb.maksAntallForsoek) Bakgrunnsjobb.Status.STOPPET else Bakgrunnsjobb.Status.FEILET

            if (nyStatus == Bakgrunnsjobb.Status.STOPPET) {
                logger.error(
                    "Jobb ${jobb.uuid} feilet permanent og ble stoppet fra å kjøre igjen. $responseBodyMessage",
                    ex,
                )
                STOPPET_JOBB_COUNTER.labels(jobb.type).inc()
                bakgrunnsvarsler.rapporterPermanentFeiletJobb()
                tryStopAction(prossessorForType, jobb)
            } else {
                logger.error("Jobb ${jobb.uuid} feilet, forsøker igjen $nesteKjoeretid. $responseBodyMessage", ex)
                FEILET_JOBB_COUNTER.labels(jobb.type).inc()
            }
        } finally {
            val oppdatertJobb =
                jobb.copy(
                    behandlet = nyBehandlet,
                    status = nyStatus,
                    kjoeretid = nesteKjoeretid,
                    forsoek = nyttForsoek,
                )

            bakgrunnsjobbRepository.update(oppdatertJobb)
        }
    }

    fun finnVentende(alle: Boolean = false): List<Bakgrunnsjobb> =
        bakgrunnsjobbRepository.findByKjoeretidBeforeAndStatusIn(
            LocalDateTime.now(),
            setOf(Bakgrunnsjobb.Status.OPPRETTET, Bakgrunnsjobb.Status.FEILET),
            alle,
        )

    private fun tryStopAction(
        prossessorForType: BakgrunnsjobbProsesserer,
        jobb: Bakgrunnsjobb,
    ) {
        try {
            prossessorForType.stoppet(jobb)
            logger.error("Jobben ${jobb.uuid} kjørte sin opprydningsjobb!")
        } catch (ex: Throwable) {
            logger.error("Jobben ${jobb.uuid} feilet i sin opprydningsjobb!", ex)
        }
    }
}

private fun tryGetResponseBody(jobException: Throwable): String? =
    if (jobException is ResponseException) {
        runCatching {
            runBlocking { jobException.response.readRawBytes().decodeToString() }
        }.getOrNull()
    } else {
        null
    }

/**
 * Interface for en klasse som kan prosessere en bakgrunnsjobbstype
 */
interface BakgrunnsjobbProsesserer {
    val type: String

    /**
     * Logikken som skal håndtere jobben. Får inn en kopi av jobben med all metadata
     */
    fun prosesser(jobb: Bakgrunnsjobb)

    /**
     * Logikk som skal kjøres når jobben stoppes helt opp fordi maks antall forsøk er nådd.
     * Får inn en kopi av jobben med all metadata
     */
    fun stoppet(jobb: Bakgrunnsjobb) {
    }

    /**
     * Default backoffløsning
     * Antall forsøk bestemmer hvor mange ganger en jobb blir forsøkt på nytt.
     * Denne metoden bestemmer hvor lang tid det tar i kalendertid før en jobb stoppes pga max forsøk.
     * Tabellen under kan brukes for å velge hvor lenge man lenge, dvs hvor mange forsøk, som forsøkes før jobbens stoppes.
     *  For eksempel hvis default backoffløsning velges med 13 antall forsøk vil jobben stoppes etter 13 forsøk
     *  fordelt over 7 dager.
     *
     *
     * Forsøk	tid mellom           Påløpte timer	Påløpte dager
     * nummer   forsøkene
     *
     *  1       	1	             1	            0,0
     *  2       	3	             4	            0,2
     *  3       	5	             9	            0,4
     *  4       	7	             16	            0,7
     *  5       	9	             25	            1,0
     *  6       	11	             36	            1,5
     *  7       	13	             49	            2,0
     *  8       	15	             64	            2,7
     *  9       	17	             81	            3,4
     *  10       	19	             100	        4,2
     *  11       	21	             121	        5,0
     *  12       	23	             144	        6,0
     *  13       	25	             169	        7,0
     *  14       	27	             196	        8,2
     *  15       	29	             225	        9,4
     *  16       	31	             256	        10,7
     *
     */
    fun nesteForsoek(
        forsoek: Int,
        forrigeForsoek: LocalDateTime,
    ): LocalDateTime {
        val backoffWaitInHours = if (forsoek == 1) 1 else forsoek - 1 + forsoek
        return LocalDateTime.now().plusHours(backoffWaitInHours.toLong())
    }
}

private val FEILET_JOBB_COUNTER =
    Counter
        .build()
        .namespace("helsearbeidsgiver")
        .name("feilet_jobb")
        .labelNames("jobbtype")
        .help("Teller jobber som har midlertidig feilet, men vil bli forsøkt igjen")
        .register()

private val STOPPET_JOBB_COUNTER =
    Counter
        .build()
        .namespace("helsearbeidsgiver")
        .name("stoppet_jobb")
        .labelNames("jobbtype")
        .help("Teller jobber som har feilet permanent og må følges opp")
        .register()

private val OK_JOBB_COUNTER =
    Counter
        .build()
        .namespace("helsearbeidsgiver")
        .name("jobb_ok")
        .labelNames("jobbtype")
        .help("Teller jobber som har blitt utført OK")
        .register()
