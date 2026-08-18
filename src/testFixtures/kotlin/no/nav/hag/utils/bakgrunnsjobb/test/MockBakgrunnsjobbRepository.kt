package no.nav.hag.utils.bakgrunnsjobb.test

import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.processing.AutoCleanJobbProcessor
import java.time.LocalDateTime
import java.util.UUID

class MockBakgrunnsjobbRepository : BakgrunnsjobbRepository {
    private val jobs = mutableMapOf<UUID, Bakgrunnsjobb>()

    override fun getById(id: UUID): Bakgrunnsjobb? = jobs[id]

    override fun save(bakgrunnsjobb: Bakgrunnsjobb) { // TODO?? mock-impl håndterer ikke duplikater likt som ekte impl
        jobs[bakgrunnsjobb.uuid] = bakgrunnsjobb
    }

    override fun update(bakgrunnsjobb: Bakgrunnsjobb) {
        delete(bakgrunnsjobb.uuid)
        save(bakgrunnsjobb)
    }

    override fun findAutoCleanJobs(): List<Bakgrunnsjobb> = jobs.values.filter { it.type == AutoCleanJobbProcessor.JOB_TYPE }

    override fun findOkAutoCleanJobs(): List<Bakgrunnsjobb> = findAutoCleanJobs()

    override fun findByKjoeretidBeforeAndStatusIn(
        timeout: LocalDateTime,
        tilstander: Set<Bakgrunnsjobb.Status>,
    ): List<Bakgrunnsjobb> =
        jobs.values
            .filter { tilstander.contains(it.status) }
            .filter { it.kjoeretid.isBefore(timeout) }

    override fun delete(uuid: UUID) {
        jobs.remove(uuid)
    }

    override fun deleteAll() {
        jobs.clear()
    }

    override fun deleteOldOkJobs(months: Long) {
        val someMonthsAgo = LocalDateTime.now().minusMonths(months)
        jobs.values
            .filter {
                it.behandlet?.isBefore(someMonthsAgo) == true && it.status == Bakgrunnsjobb.Status.OK
            }.map { it.uuid }
            .forEach {
                jobs.remove(it)
            }
    }
}
