package no.nav.hag.utils.bakgrunnsjobb.processing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService

class AutoCleanJobbProcessor(
    private val bakgrunnsjobbRepository: BakgrunnsjobbRepository,
    private val bakgrunnsjobbService: BakgrunnsjobbService,
    private val om: ObjectMapper,
) : BakgrunnsjobbProsesserer {
    companion object {
        val JOB_TYPE = "bakgrunnsjobb-autoclean"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        assert(jobb.data.isNotEmpty())
        val autocleanrequest = om.readValue<JobbData>(jobb.data)
        bakgrunnsjobbRepository.deleteOldOkJobs(autocleanrequest.slettEldre)
        bakgrunnsjobbService.startAutoClean(autocleanrequest.interval, autocleanrequest.slettEldre)
    }

    data class JobbData(
        val slettEldre: Long,
        var interval: Int,
    )
}
