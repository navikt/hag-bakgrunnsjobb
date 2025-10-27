package no.nav.hag.utils.bakgrunnsjobb

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import no.nav.hag.utils.bakgrunnsjobb.config.Database
import no.nav.hag.utils.bakgrunnsjobb.config.createLocalHikariConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class BakgrunnsjobbServiceTest {
    private val dataSource = HikariDataSource(createLocalHikariConfig())
    private val repository = PostgresBakgrunnsjobbRepository(dataSource)
    private val testCoroutineScope = TestScope()
    private val service = BakgrunnsjobbService(repository, 1, testCoroutineScope)

    private val now = LocalDateTime.now()
    private val eksempelProsesserer = EksempelProsesserer()

    companion object {
        @JvmStatic
        @BeforeAll
        fun migrateDb() {
            Database(createLocalHikariConfig()).migrate()
        }
    }

    @BeforeEach
    internal fun setup() {
        service.registrer(eksempelProsesserer)
        service.startAsync(true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sjekk ytelse `() {
        for (i in 1..1000) {
            val uuid = UUID.randomUUID()
            val data = """{"status": "ok", "uuid": "$uuid" }"""
            val testJobb =
                Bakgrunnsjobb(
                    type = EksempelProsesserer.JOBB_TYPE,
                    data = data,
                )
            repository.save(testJobb)
        }
        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        val resultSet = repository.findByKjoeretidBeforeAndStatusIn(LocalDateTime.now(), setOf(BakgrunnsjobbStatus.OK), true)
        assertThat(resultSet)
            .hasSize(1000)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sett jobb til ok hvis ingen feil `() {
        val data = """{"status": "ok"}"""
        val testJobb =
            Bakgrunnsjobb(
                type = EksempelProsesserer.JOBB_TYPE,
                data = data,
            )
        repository.save(testJobb)
        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        val resultSet = repository.findByKjoeretidBeforeAndStatusIn(LocalDateTime.now(), setOf(BakgrunnsjobbStatus.OK), false)
        assertThat(resultSet)
            .hasSize(1)

        val completeJob = resultSet[0]
        assertThat(completeJob.forsoek).isEqualTo(1)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sett jobb til stoppet og kjør stoppet-funksjonen hvis feiler for mye `() {
        val testJobb =
            Bakgrunnsjobb(
                type = EksempelProsesserer.JOBB_TYPE,
                opprettet = now.minusHours(1),
                maksAntallForsoek = 3,
                data = """{"status": "fail"}""",
            )
        repository.save(testJobb)
        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        // Den går rett til stoppet i denne testen
        assertThat(repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.STOPPET), false))
            .hasSize(1)

        assertThat(eksempelProsesserer.bleStoppet).isTrue()
    }

    @Test
    fun `autoClean opprettes feil parametre`() {
        var exception =
            Assertions.assertThrows(IllegalArgumentException::class.java) {
                service.startAutoClean(-1, 3)
            }
        Assertions.assertEquals("start autoclean må ha en frekvens større enn 1 og slettEldreEnnMaander større enn 0", exception.message)
        exception =
            Assertions.assertThrows(IllegalArgumentException::class.java) {
                service.startAutoClean(1, -1)
            }
        Assertions.assertEquals("start autoclean må ha en frekvens større enn 1 og slettEldreEnnMaander større enn 0", exception.message)
        assertThat(repository.findAutoCleanJobs()).hasSize(0)
    }

    @Test
    fun `autoClean opprettes med riktig kjøretid`() {
        service.startAutoClean(2, 3)
        assertThat(repository.findAutoCleanJobs()).hasSize(1)
        assert(
            repository.findAutoCleanJobs().get(0).kjoeretid > now.plusHours(1) &&
                repository.findAutoCleanJobs().get(0).kjoeretid < now.plusHours(3),
        )
    }

    @Test
    fun `autoClean oppretter jobb med riktig antall måneder`() {
        service.startAutoClean(2, 3)
        assertThat(repository.findAutoCleanJobs()).hasSize(1)
    }

    @Test
    fun `opprett lager korrekt jobb`() {
        val data = """{"status": "ok"}"""
        dataSource.connection.use {
            service.opprettJobb<EksempelProsesserer>(data = data)
        }
        val jobber =
            repository.findByKjoeretidBeforeAndStatusIn(LocalDateTime.now().plusDays(1), setOf(BakgrunnsjobbStatus.OPPRETTET), false)
        assertThat(jobber).hasSize(1)
        assertThat(jobber[0].type).isEqualTo(EksempelProsesserer.JOBB_TYPE)
        assertThat(jobber[0].data).isEqualTo(data)
    }

    @AfterEach
    fun teardown() {
        repository.deleteAll()
    }
}

class EksempelProsesserer : BakgrunnsjobbProsesserer {
    companion object {
        val JOBB_TYPE: String = "TEST_TYPE"
    }

    var bleStoppet: Boolean = false

    override val type = JOBB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        if (jobb.data == """{"status": "fail"}""") {
            throw RuntimeException()
        }
    }

    override fun stoppet(jobb: Bakgrunnsjobb) {
        bleStoppet = true
        throw RuntimeException()
    }

    override fun nesteForsoek(
        forsoek: Int,
        forrigeForsoek: LocalDateTime,
    ): LocalDateTime = LocalDateTime.now()
}
