package no.nav.hag.utils.bakgrunnsjobb.autoclean

import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helsearbeidsgiver.utils.log.logger
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime

class AutoCleanJobbProcessor(
    private val bakgrunnsjobbRepository: BakgrunnsjobbRepository,
    private val bakgrunnsjobbService: BakgrunnsjobbService,
) : BakgrunnsjobbProsesserer {
    companion object {
        const val JOB_TYPE = "bakgrunnsjobb-autoclean"
    }

    data class JobbData(
        val slettEldre: Long,
        val interval: Int,
    )

    private val logger = logger()

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        assert(jobb.data.isNotEmpty())
        val autocleanRequest = Jackson.fromJson(jobb.data)
        bakgrunnsjobbRepository.deleteOldOkJobs(autocleanRequest.slettEldre)
        startAutoClean(autocleanRequest.interval, autocleanRequest.slettEldre)
    }

    private fun startAutoClean(
        frekvensITimer: Int,
        slettEldreEnnMaaneder: Long,
    ) {
        if (frekvensITimer < 1 || slettEldreEnnMaaneder < 0) {
            logger.info("start autoclean forsøkt startet med ugyldige parametre.")
            throw IllegalArgumentException("start autoclean må ha en frekvens større enn 1 og slettEldreEnnMaander større enn 0")
        } else if (bakgrunnsjobbService.isRunning) {
            val autocleanjobber = bakgrunnsjobbRepository.findAutoCleanJobs()

            if (autocleanjobber.isEmpty()) {
                val data =
                    Jackson.toJson(
                        JobbData(
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
}

private object Jackson {
    private val om: ObjectMapper = jacksonObjectMapper()

    fun toJson(data: AutoCleanJobbProcessor.JobbData): String = om.writeValueAsString(data)

    fun fromJson(data: String): AutoCleanJobbProcessor.JobbData = om.readValue(data)
}
