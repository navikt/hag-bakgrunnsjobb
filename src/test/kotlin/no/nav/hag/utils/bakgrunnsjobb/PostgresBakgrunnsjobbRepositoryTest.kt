package no.nav.hag.utils.bakgrunnsjobb

import com.zaxxer.hikari.HikariDataSource
import no.nav.hag.utils.bakgrunnsjobb.config.createLocalHikariConfig
import no.nav.hag.utils.bakgrunnsjobb.processing.AutoCleanJobbProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PostgresBakgrunnsjobbRepositoryTest {
    lateinit var repo: PostgresBakgrunnsjobbRepository
    lateinit var dataSource: HikariDataSource
    val now = LocalDateTime.now()

    @BeforeEach
    internal fun setUp() {
        dataSource = HikariDataSource(createLocalHikariConfig())
        repo = PostgresBakgrunnsjobbRepository(dataSource)
    }

    @AfterEach
    internal fun cleanUp() {
        repo.deleteAll()
    }

    @Test
    fun `Lagre Les Oppdater Slett`() {
        val uuid = UUID.randomUUID()
        val bakgrunnsjobb =
            Bakgrunnsjobb(
                uuid,
                "test",
                now,
                now,
                BakgrunnsjobbStatus.OPPRETTET,
                now,
                0,
                3,
                "{}",
            )

        repo.save(bakgrunnsjobb)

        val jobs = repo.findByKjoeretidBeforeAndStatusIn(now.plusHours(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true)
        assertThat(jobs).hasSize(1)

        val job = jobs.first()
        assertThat(job.uuid).isEqualTo(uuid)
        assertThat(job.type).isEqualTo("test")
        assertThat(job.opprettet).isEqualToIgnoringNanos(now)
        assertThat(job.behandlet).isEqualToIgnoringNanos(now)
        assertThat(job.kjoeretid).isEqualToIgnoringNanos(now)
        assertThat(job.status).isEqualTo(BakgrunnsjobbStatus.OPPRETTET)
        assertThat(job.forsoek).isEqualTo(0)
        assertThat(job.maksAntallForsoek).isEqualTo(3)
        assertThat(job.data).isEqualTo("{}")

        job.status = BakgrunnsjobbStatus.FEILET

        repo.update(job)

        val failedJobs = repo.findByKjoeretidBeforeAndStatusIn(now.plusHours(1), setOf(BakgrunnsjobbStatus.FEILET), true)
        assertThat(failedJobs).hasSize(1)

        repo.delete(job.uuid)

        val noJobs = repo.findByKjoeretidBeforeAndStatusIn(now.plusHours(1), setOf(BakgrunnsjobbStatus.FEILET), true)
        assertThat(noJobs).isEmpty()
    }

    @Test
    fun `find autoclean jobs`() {
        val uuid = UUID.randomUUID()
        val bakgrunnsjobb =
            Bakgrunnsjobb(
                uuid,
                AutoCleanJobbProcessor.JOB_TYPE,
                now,
                now,
                BakgrunnsjobbStatus.OPPRETTET,
                now,
                0,
                3,
                "{}",
            )
        assertThat(repo.findAutoCleanJobs()).hasSize(0)
        repo.save(bakgrunnsjobb)
        assertThat(repo.findAutoCleanJobs()).hasSize(1)
    }

    @Test
    fun `get by id`() {
        val uuid = UUID.randomUUID()
        assertThat(repo.getById(uuid)).isNull()
    }

    @Test
    fun `håndter null`() {
        val uuid = UUID.randomUUID()
        val bakgrunnsjobb =
            Bakgrunnsjobb(
                uuid,
                "test",
                null,
                now,
                BakgrunnsjobbStatus.OPPRETTET,
                now,
                0,
                3,
                "{}",
            )

        repo.save(bakgrunnsjobb)

        val jobs = repo.findByKjoeretidBeforeAndStatusIn(now.plusHours(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true)
        assertThat(jobs).hasSize(1)
        assertThat(jobs.first().behandlet).isNull()
    }

    @Test
    fun `job gets deleted if older than input`() {
        val uuid = UUID.randomUUID()
        val bakgrunnsjobb =
            Bakgrunnsjobb(
                uuid,
                "bakgrunnsjobb-autoclean",
                now.minusMonths(3),
                now,
                BakgrunnsjobbStatus.OK,
                now.minusMonths(3),
                0,
                3,
                "{}",
            )
        repo.save(bakgrunnsjobb)
        assertThat(repo.findOkAutoCleanJobs()).hasSize(1)
        repo.deleteOldOkJobs(2)
        assertThat(repo.findOkAutoCleanJobs()).hasSize(0)
    }

    @Test
    fun `job do not get deleted if not older than input`() {
        val uuid = UUID.randomUUID()
        val bakgrunnsjobb =
            Bakgrunnsjobb(
                uuid,
                "bakgrunnsjobb-autoclean",
                now.minusMonths(2),
                now,
                BakgrunnsjobbStatus.OK,
                now.minusMonths(2),
                0,
                3,
                "{}",
            )
        repo.save(bakgrunnsjobb)
        assertThat(repo.findOkAutoCleanJobs()).hasSize(1)
        repo.deleteOldOkJobs(2)
        assertThat(repo.findOkAutoCleanJobs()).hasSize(1)
    }
}
