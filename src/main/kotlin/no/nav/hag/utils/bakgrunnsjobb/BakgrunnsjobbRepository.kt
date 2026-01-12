package no.nav.hag.utils.bakgrunnsjobb

import no.nav.hag.utils.bakgrunnsjobb.processing.AutoCleanJobbProcessor
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

interface BakgrunnsjobbRepository {
    fun getById(id: UUID): Bakgrunnsjobb?

    fun save(bakgrunnsjobb: Bakgrunnsjobb)

    fun update(bakgrunnsjobb: Bakgrunnsjobb)

    fun findAutoCleanJobs(): List<Bakgrunnsjobb>

    fun findOkAutoCleanJobs(): List<Bakgrunnsjobb>

    fun findByKjoeretidBeforeAndStatusIn(
        timeout: LocalDateTime,
        tilstander: Set<Bakgrunnsjobb.Status>,
        alle: Boolean,
    ): List<Bakgrunnsjobb>

    fun delete(uuid: UUID)

    fun deleteAll()

    fun deleteOldOkJobs(months: Long)
}

class MockBakgrunnsjobbRepository : BakgrunnsjobbRepository {
    private val jobs = mutableMapOf<UUID, Bakgrunnsjobb>()

    override fun getById(id: UUID): Bakgrunnsjobb? = jobs[id]

    override fun save(bakgrunnsjobb: Bakgrunnsjobb) { // TODO?? mock-impl håndterer ikke duplikater likt som ekte impl
        jobs.put(bakgrunnsjobb.uuid, bakgrunnsjobb)
    }

    override fun update(bakgrunnsjobb: Bakgrunnsjobb) {
        delete(bakgrunnsjobb.uuid)
        save(bakgrunnsjobb)
    }

    override fun findAutoCleanJobs(): List<Bakgrunnsjobb> = jobs.values.filter { it.type == AutoCleanJobbProcessor.JOB_TYPE }

    override fun findOkAutoCleanJobs(): List<Bakgrunnsjobb> = jobs.values.filter { it.type == AutoCleanJobbProcessor.JOB_TYPE }

    override fun findByKjoeretidBeforeAndStatusIn(
        timeout: LocalDateTime,
        tilstander: Set<Bakgrunnsjobb.Status>,
        alle: Boolean,
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

class PostgresBakgrunnsjobbRepository(
    val dataSource: DataSource,
) : BakgrunnsjobbRepository {
    override fun getById(id: UUID): Bakgrunnsjobb? =
        executeQuery(BakgrunnsjobbTable.selectByIdStatement) {
            it.setString(1, id.toString())
        }.firstOrNull()

    override fun save(bakgrunnsjobb: Bakgrunnsjobb) {
        executeUpdate(BakgrunnsjobbTable.insertStatement) {
            it.setString(1, bakgrunnsjobb.uuid.toString())
            it.setString(2, bakgrunnsjobb.type)
            it.setTimestamp(3, bakgrunnsjobb.behandlet?.toTimestamp())
            it.setTimestamp(4, bakgrunnsjobb.opprettet.toTimestamp())
            it.setString(5, bakgrunnsjobb.status.toString())
            it.setTimestamp(6, bakgrunnsjobb.kjoeretid.toTimestamp())
            it.setInt(7, bakgrunnsjobb.forsoek)
            it.setInt(8, bakgrunnsjobb.maksAntallForsoek)
            it.setString(9, bakgrunnsjobb.data)
        }
    }

    override fun update(bakgrunnsjobb: Bakgrunnsjobb) {
        executeUpdate(BakgrunnsjobbTable.updateStatement) {
            it.setTimestamp(1, bakgrunnsjobb.behandlet?.toTimestamp())
            it.setString(2, bakgrunnsjobb.status.toString())
            it.setTimestamp(3, bakgrunnsjobb.kjoeretid.toTimestamp())
            it.setInt(4, bakgrunnsjobb.forsoek)
            it.setString(5, bakgrunnsjobb.data)
            it.setString(6, bakgrunnsjobb.uuid.toString())
        }
    }

    override fun findAutoCleanJobs(): List<Bakgrunnsjobb> = executeQuery(BakgrunnsjobbTable.selectAutoCleanStatement)

    override fun findOkAutoCleanJobs(): List<Bakgrunnsjobb> = executeQuery(BakgrunnsjobbTable.selectOkAutoCleanStatement)

    override fun findByKjoeretidBeforeAndStatusIn(
        timeout: LocalDateTime,
        tilstander: Set<Bakgrunnsjobb.Status>,
        alle: Boolean,
    ): List<Bakgrunnsjobb> {
        val tilstanderArray = tilstander.map(Bakgrunnsjobb.Status::toString).toTypedArray()

        val selectStatement =
            if (alle) {
                BakgrunnsjobbTable.selectStatement
            } else {
                BakgrunnsjobbTable.selectWithLimitStatement
            }

        return executeQuery(selectStatement) { con, ps ->
            ps.setTimestamp(1, timeout.toTimestamp())
            ps.setArray(2, con.createArrayOf("VARCHAR", tilstanderArray))
        }
    }

    override fun delete(uuid: UUID) {
        executeUpdate(BakgrunnsjobbTable.deleteStatement) {
            it.setString(1, uuid.toString())
        }
    }

    override fun deleteAll() {
        executeUpdate(BakgrunnsjobbTable.deleteAllStatement)
    }

    override fun deleteOldOkJobs(months: Long) {
        val deleteBefore = LocalDate.now().minusMonths(months).let(Date::valueOf)

        executeUpdate(BakgrunnsjobbTable.deleteOldOkJobsStatement) {
            it.setDate(1, deleteBefore)
        }
    }

    private fun executeQuery(
        statement: String,
        setParameters: (PreparedStatement) -> Unit = {},
    ): List<Bakgrunnsjobb> = executeQuery(statement) { _, ps -> setParameters(ps) }

    private fun executeQuery(
        statement: String,
        setParameters: (Connection, PreparedStatement) -> Unit,
    ): List<Bakgrunnsjobb> =
        dataSource.connection.use { con ->
            con
                .prepareStatement(statement)
                .also {
                    setParameters(con, it)
                }.use {
                    it.executeQuery().tilBakgrunnsjobber()
                }
        }

    private fun executeUpdate(
        statement: String,
        setParameters: (PreparedStatement) -> Unit = {},
    ) {
        dataSource.connection.use { con ->
            con
                .prepareStatement(statement)
                .also {
                    setParameters(it)
                }.use {
                    it.executeUpdate()
                }
        }
    }
}

private fun LocalDateTime.toTimestamp(): Timestamp = Timestamp.valueOf(this)

private fun ResultSet.tilBakgrunnsjobber(): List<Bakgrunnsjobb> =
    use {
        generateSequence { if (it.next()) it else null }
            .map { rs ->
                Bakgrunnsjobb(
                    uuid = "jobb_id".readString(rs).let(UUID::fromString),
                    type = "type".readString(rs),
                    behandlet = "behandlet".readTimeNullable(rs),
                    opprettet = "opprettet".readTime(rs),
                    status = "status".readString(rs).let(Bakgrunnsjobb.Status::valueOf),
                    kjoeretid = "kjoeretid".readTime(rs),
                    forsoek = "forsoek".readInt(rs),
                    maksAntallForsoek = "maks_forsoek".readInt(rs),
                    data = "data".readString(rs),
                )
            }.toList()
    }

private fun String.readString(rs: ResultSet): String = rs.getString(this)

private fun String.readInt(rs: ResultSet): Int = rs.getInt(this)

private fun String.readTime(rs: ResultSet): LocalDateTime = rs.getTimestamp(this).toLocalDateTime()

private fun String.readTimeNullable(rs: ResultSet): LocalDateTime? = rs.getTimestamp(this)?.toLocalDateTime()
