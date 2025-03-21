package no.nav.hag.utils.bakgrunnsjobb.exposed

import kotlinx.serialization.json.Json
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.behandlet
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.data
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.forsoek
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.jobbId
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.kjoeretid
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.maksForsoek
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.opprettet
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.status
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobb.type
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID
import javax.management.Query.eq

class ExposedBakgrunnsjobRepository(private val db: Database) : BakgrunnsjobbRepository {
    override fun getById(id: UUID): Bakgrunnsjobb? {
        return transaction(db) {
            ExposedBakgrunnsjobb.selectAll().where(jobbId.eq(id)).map {
                it.toBakgrunnsjobb()
            }.singleOrNull()
        }
    }


    override fun save(bakgrunnsjobb: Bakgrunnsjobb) {
       transaction(db) {
            ExposedBakgrunnsjobb.insert {
                it[jobbId] = bakgrunnsjobb.uuid
                it[type] = bakgrunnsjobb.type
                it[opprettet] = bakgrunnsjobb.opprettet
                it[behandlet] = bakgrunnsjobb.behandlet
                it[status] = bakgrunnsjobb.status
                it[kjoeretid] = bakgrunnsjobb.kjoeretid
                it[forsoek] = bakgrunnsjobb.forsoek
                it[maksForsoek] = bakgrunnsjobb.maksAntallForsoek
                it[data] = bakgrunnsjobb.data
            }
        }
       }

    override fun findAutoCleanJobs(): List<Bakgrunnsjobb> {
        TODO("Not yet implemented")
    }

    override fun findOkAutoCleanJobs(): List<Bakgrunnsjobb> {
        TODO("Not yet implemented")
    }

    override fun findByKjoeretidBeforeAndStatusIn(timeout: LocalDateTime, tilstander: Set<BakgrunnsjobbStatus>, alle: Boolean): List<Bakgrunnsjobb> {
        return transaction(db) {
            val query = ExposedBakgrunnsjobb.selectAll().where{(kjoeretid lessEq timeout) and (status inList tilstander)}
            if (!alle) {
                query.limit(100)
            }
            query.map { it.toBakgrunnsjobb() }
        }
    }

    override fun delete(uuid: UUID) {
       transaction(db) {
            ExposedBakgrunnsjobb.deleteWhere { jobbId eq uuid }
       }
    }

    override fun deleteAll() {
        transaction(db) {
            ExposedBakgrunnsjobb.deleteAll()
        }
    }

    override fun deleteOldOkJobs(months: Long) {
        TODO("Not yet implemented")
    }

    override fun update(bakgrunnsjobb: Bakgrunnsjobb) {
       save(bakgrunnsjobb)
    }

}

private fun ResultRow.toBakgrunnsjobb(): Bakgrunnsjobb {
    return Bakgrunnsjobb(
        uuid = this[jobbId],
        type = this[type],
        opprettet = this[opprettet],
        behandlet = this[behandlet],
        status = this[status],
        kjoeretid = this[kjoeretid],
        forsoek = this[forsoek],
        maksAntallForsoek = this[maksForsoek],
        data = this[data]
    )
}
