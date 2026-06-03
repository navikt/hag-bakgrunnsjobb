package no.nav.hag.utils.bakgrunnsjobb.processing

import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.hag.utils.bakgrunnsjobb.Jackson

class AutoCleanJobbProcessor(
    private val bakgrunnsjobbRepository: BakgrunnsjobbRepository,
    private val bakgrunnsjobbService: BakgrunnsjobbService,
) : BakgrunnsjobbProsesserer {
    companion object {
        const val JOB_TYPE = "bakgrunnsjobb-autoclean"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        assert(jobb.data.isNotEmpty())
        val autocleanRequest = Jackson.fromJson(jobb.data)
        bakgrunnsjobbRepository.deleteOldOkJobs(autocleanRequest.slettEldre)
        bakgrunnsjobbService.startAutoClean(autocleanRequest.interval, autocleanRequest.slettEldre)
    }

    data class JobbData(
        val slettEldre: Long,
        val interval: Int,
    )
}
