package no.nav.hag.utils.bakgrunnsjobb

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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BakgrunnsjobbService(
    val bakgrunnsjobbRepository: BakgrunnsjobbRepository,
    interval: Duration = 30.seconds,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val vedEndeligFeil: () -> Unit = {},
) : RecurringJob(interval, coroutineScope) {
    val prossesserere = HashMap<String, BakgrunnsjobbProsesserer>()

    override fun doJob() {
        do {
            val wasEmpty =
                finnVentende()
                    .filtrerUtJobberUtenProsessor()
                    .onEach { prosesser(it.first, it.second) }
                    .isEmpty()
        } while (!wasEmpty)
    }

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
                    Jackson.toJson(
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

    private fun prosesser(
        prossessorForType: BakgrunnsjobbProsesserer,
        jobb: Bakgrunnsjobb,
    ) {
        val nyBehandlet = LocalDateTime.now()
        val nyttForsoek = jobb.forsoek + 1

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
                vedEndeligFeil()
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

    private fun finnVentende(alle: Boolean = false): List<Bakgrunnsjobb> =
        bakgrunnsjobbRepository.findByKjoeretidBeforeAndStatusIn(
            LocalDateTime.now(),
            setOf(Bakgrunnsjobb.Status.OPPRETTET, Bakgrunnsjobb.Status.FEILET),
            alle,
        )

    private fun List<Bakgrunnsjobb>.filtrerUtJobberUtenProsessor(): List<Pair<BakgrunnsjobbProsesserer, Bakgrunnsjobb>> {
        val jobberMedProsessor = mapNotNull { prossesserere[it.type]?.to(it) }

        if (size == jobberMedProsessor.size) {
            logger.debug("Fant $size bakgrunnsjobber å kjøre.")
        } else {
            logger.error(
                "Fant ${jobberMedProsessor.size} bakgrunnsjobber å kjøre. ${size - jobberMedProsessor.size} kunne ikke kjøres pga. manglende prosesserer.",
            )
        }

        return jobberMedProsessor
    }

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
