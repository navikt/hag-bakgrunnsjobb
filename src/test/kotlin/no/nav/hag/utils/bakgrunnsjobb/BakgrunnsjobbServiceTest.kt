package no.nav.hag.utils.bakgrunnsjobb

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import no.nav.hag.utils.bakgrunnsjobb.config.WithPostgresContainer
import no.nav.hag.utils.bakgrunnsjobb.config.createHikariConfig
import no.nav.hag.utils.bakgrunnsjobb.config.migrate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BakgrunnsjobbServiceTest : WithPostgresContainer() {
    val hikariConfig = createHikariConfig(postgresContainer)
    val dataSource = HikariDataSource(hikariConfig)
    val repository = PostgresBakgrunnsjobbRepository(dataSource)

    val testCoroutineScope = TestScope()
    val service = BakgrunnsjobbService(repository, 1.milliseconds, testCoroutineScope)

    val now: LocalDateTime = LocalDateTime.now()
    val eksempelProsesserer = EksempelProsesserer()

    @BeforeAll
    fun migrateDb() {
        migrate(hikariConfig)
    }

    @BeforeEach
    fun setup() {
        service.registrer(eksempelProsesserer)
        service.startAsync(true)
    }

    @Test
    fun `sjekk ytelse `() {
        val ider =
            List(1000) {
                val uuid = UUID.randomUUID()
                val data = """{"dokumentId": "123", "uuid": "$uuid" }"""
                val testJobb =
                    Bakgrunnsjobb(
                        type = EksempelProsesserer.JOBB_TYPE,
                        data = data,
                    )
                repository.save(testJobb)
                testJobb.uuid to uuid
            }

        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        ider.forEach { (jobbId, dataId) ->
            repository.getById(jobbId).also {
                assertThat(it?.status).isEqualTo(Bakgrunnsjobb.Status.OK)
                assertThat(it?.data).contains(dataId.toString())
            }
        }
    }

    @Test
    fun `sett jobb til ok hvis ingen feil `() {
        val data = """{"dokumentId": "123"}"""
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

        val resultSet = repository.findByKjoeretidBeforeAndStatusIn(LocalDateTime.now(), setOf(Bakgrunnsjobb.Status.OK))
        assertThat(resultSet)
            .hasSize(1)

        val completeJob = resultSet[0]
        assertThat(completeJob.forsoek).isEqualTo(1)
    }

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
        assertThat(
            repository.findByKjoeretidBeforeAndStatusIn(
                now.plusMinutes(1),
                setOf(Bakgrunnsjobb.Status.STOPPET),
            ),
        ).hasSize(1)

        assertThat(eksempelProsesserer.bleStoppet).isTrue()
    }

    @Test
    fun `utsatt jobb beholder status og forsøk og får ny kjøretid`() {
        val testJobb =
            Bakgrunnsjobb(
                type = EksempelProsesserer.JOBB_TYPE,
                data = """{"status": "utsett"}""",
            )
        repository.save(testJobb)
        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        val utsattJobb = repository.getById(testJobb.uuid)
        assertThat(utsattJobb?.status).isEqualTo(Bakgrunnsjobb.Status.OPPRETTET)
        assertThat(utsattJobb?.forsoek).isEqualTo(0)
        assertThat(utsattJobb?.behandlet).isNull()
        assertThat(utsattJobb?.kjoeretid).isAfter(LocalDateTime.now().plusMinutes(59))
    }

    @Test
    fun `ny jobb utsettes når eldre jobb feiler, og eldre jobb prosesseres først`() {
        val rekkefoelgeProsesserer = RekkefoelgeProsesserer(repository)
        service.registrer(rekkefoelgeProsesserer)

        val jobbA =
            Bakgrunnsjobb(
                type = RekkefoelgeProsesserer.JOBB_TYPE,
                opprettet = now.minusMinutes(2),
                kjoeretid = now.minusMinutes(2),
                maksAntallForsoek = 3,
                data = """{"status": "fail"}""",
            )
        val jobbB =
            Bakgrunnsjobb(
                type = RekkefoelgeProsesserer.JOBB_TYPE,
                opprettet = now.minusMinutes(1),
                kjoeretid = now.minusMinutes(1),
                data = """{"venterPaa": "${jobbA.uuid}"}""",
            )
        rekkefoelgeProsesserer.venterPaa = jobbA.uuid

        // Lagres i omvendt rekkefølge for å vise at jobbene plukkes etter opprettet.
        repository.save(jobbB)
        repository.save(jobbA)

        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        assertThat(rekkefoelgeProsesserer.prosesserteJobber).containsExactly(jobbA.uuid, jobbB.uuid)

        val feiletJobbA = repository.getById(jobbA.uuid)
        assertThat(feiletJobbA?.status).isEqualTo(Bakgrunnsjobb.Status.FEILET)
        assertThat(feiletJobbA?.forsoek).isEqualTo(1)

        val utsattJobbB = repository.getById(jobbB.uuid)
        assertThat(utsattJobbB?.status).isEqualTo(Bakgrunnsjobb.Status.OPPRETTET)
        assertThat(utsattJobbB?.forsoek).isEqualTo(0)
        assertThat(utsattJobbB?.behandlet).isNull()
        assertThat(utsattJobbB?.kjoeretid).isAfter(LocalDateTime.now().plusMinutes(59))
    }

    @Test
    fun `utsettelse må være positiv`() {
        assertThatThrownBy { UtsettJobbException(Duration.ZERO, "vent") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `opprett lager korrekt jobb`() {
        val data = """{"dokumentId": "123"}"""
        dataSource.connection.use {
            service.opprettJobb<EksempelProsesserer>(data = data)
        }
        val jobber =
            repository.findByKjoeretidBeforeAndStatusIn(
                LocalDateTime.now().plusDays(1),
                setOf(Bakgrunnsjobb.Status.OPPRETTET),
            )
        assertThat(jobber).hasSize(1)
        assertThat(jobber[0].type).isEqualTo(EksempelProsesserer.JOBB_TYPE)
        assertThat(jobber[0].data).isEqualTo(data)
    }

    @Test
    fun `kjører ikke jobber når pod ikke er leder`() {
        val ikkeLederScope = TestScope()
        val ikkeLederService =
            BakgrunnsjobbService(repository, 1.milliseconds, ikkeLederScope, leaderConfig = { false })
        ikkeLederService.registrer(eksempelProsesserer)
        service.stop()
        ikkeLederService.startAsync(true)

        val testJobb =
            Bakgrunnsjobb(
                type = EksempelProsesserer.JOBB_TYPE,
                data = """{"dokumentId": "123"}""",
            )
        repository.save(testJobb)
        ikkeLederScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }
        ikkeLederService.stop()

        assertThat(repository.getById(testJobb.uuid)?.status).isEqualTo(Bakgrunnsjobb.Status.OPPRETTET)
    }

    @Test
    fun `kjører jobber når pod er leder`() {
        val lederScope = TestScope()
        val lederService =
            BakgrunnsjobbService(repository, 1.milliseconds, lederScope, leaderConfig = { true })
        lederService.registrer(eksempelProsesserer)
        service.stop()
        lederService.startAsync(true)

        val testJobb =
            Bakgrunnsjobb(
                type = EksempelProsesserer.JOBB_TYPE,
                data = """{"dokumentId": "123"}""",
            )
        repository.save(testJobb)
        lederScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }
        lederService.stop()

        assertThat(repository.getById(testJobb.uuid)?.status).isEqualTo(Bakgrunnsjobb.Status.OK)
    }

    @AfterEach
    fun teardown() {
        repository.deleteAll()
    }
}

class RekkefoelgeProsesserer(
    private val repository: BakgrunnsjobbRepository,
) : BakgrunnsjobbProsesserer {
    companion object {
        const val JOBB_TYPE = "REKKEFOELGE_TYPE"
    }

    var venterPaa: UUID? = null
    val prosesserteJobber = mutableListOf<UUID>()

    override val type = JOBB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        prosesserteJobber.add(jobb.uuid)
        if (jobb.data == """{"status": "fail"}""") {
            throw RuntimeException()
        }
        val eldreJobb = venterPaa?.let(repository::getById)
        if (eldreJobb != null && eldreJobb.status != Bakgrunnsjobb.Status.OK) {
            throw UtsettJobbException(1.hours, "Venter på ${eldreJobb.uuid}")
        }
    }
}

class EksempelProsesserer : BakgrunnsjobbProsesserer {
    companion object {
        const val JOBB_TYPE = "TEST_TYPE"
    }

    var bleStoppet = false

    override val type = JOBB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        if (jobb.data == """{"status": "fail"}""") {
            throw RuntimeException()
        }
        if (jobb.data == """{"status": "utsett"}""") {
            throw UtsettJobbException(1.hours, "Venter")
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
