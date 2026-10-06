package no.nav.hag.utils.bakgrunnsjobb.autoclean

import kotlinx.coroutines.test.TestScope
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.hag.utils.bakgrunnsjobb.MockBakgrunnsjobbRepository
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class AutoCleanJobbProcessorTest {
    val now: LocalDateTime = LocalDateTime.now()
    lateinit var autoCleanJobbProcessor: AutoCleanJobbProcessor
    lateinit var bakgrunnsjobbRepository: BakgrunnsjobbRepository
    lateinit var bakgrunnsjobbService: BakgrunnsjobbService
    val bakgrunnsjobbSlettEldreEnn10 =
        Bakgrunnsjobb(
            UUID.randomUUID(),
            AutoCleanJobbProcessor.JOB_TYPE,
            now,
            now,
            Bakgrunnsjobb.Status.OPPRETTET,
            now,
            0,
            3,
            "{\"slettEldre\": \"10\",\"interval\": \"3\"}",
        )
    val bakgrunnsjobbSlettEldreEnn2 =
        Bakgrunnsjobb(
            UUID.randomUUID(),
            AutoCleanJobbProcessor.JOB_TYPE,
            now,
            now,
            Bakgrunnsjobb.Status.OPPRETTET,
            now,
            0,
            3,
            "{\"slettEldre\": \"2\",\"interval\": \"3\"}",
        )
    val bakgrunnsjobb3mndGammel =
        Bakgrunnsjobb(
            UUID.randomUUID(),
            "test",
            now.minusMonths(3),
            now.minusMonths(3),
            Bakgrunnsjobb.Status.OK,
            now.minusMonths(3),
            0,
            3,
            "{}",
        )

    @BeforeEach
    fun setUp() {
        bakgrunnsjobbRepository = MockBakgrunnsjobbRepository()
        val testScope = TestScope()
        bakgrunnsjobbService =
            BakgrunnsjobbService(bakgrunnsjobbRepository, 1.milliseconds, testScope)
                .also { it.startAsync() }
        autoCleanJobbProcessor = AutoCleanJobbProcessor(bakgrunnsjobbRepository, bakgrunnsjobbService)
    }

    @Test
    fun getType() {
        Assertions.assertThat(AutoCleanJobbProcessor.JOB_TYPE == autoCleanJobbProcessor.type).isTrue()
    }

    @Test
    fun `autoClean opprettes feil parametre`() {
        val exceptionNegativFrekvens =
            assertThrows(IllegalArgumentException::class.java) {
                autoCleanJobbProcessor.prosesser(
                    bakgrunnsjobbSlettEldreEnn10.copy(
                        data = "{\"slettEldre\": \"1\",\"interval\": \"-1\"}",
                    ),
                )
            }
        assertEquals(
            "start autoclean må ha en frekvens større enn 1 og slettEldreEnnMaander større enn 0",
            exceptionNegativFrekvens.message,
        )
        val exceptionNegativSlettemengde =
            assertThrows(IllegalArgumentException::class.java) {
                autoCleanJobbProcessor.prosesser(
                    bakgrunnsjobbSlettEldreEnn10.copy(
                        data = "{\"slettEldre\": \"-1\",\"interval\": \"2\"}",
                    ),
                )
            }
        assertEquals(
            "start autoclean må ha en frekvens større enn 1 og slettEldreEnnMaander større enn 0",
            exceptionNegativSlettemengde.message,
        )
        assertThat(bakgrunnsjobbRepository.findAutoCleanJobs()).hasSize(0)
    }

    @Test
    fun `autoClean opprettes med riktig kjøretid`() {
        autoCleanJobbProcessor.prosesser(bakgrunnsjobbSlettEldreEnn10)
        bakgrunnsjobbRepository.findAutoCleanJobs().also { jobber ->
            assertThat(jobber).hasSize(1)
            assert(
                jobber[0].kjoeretid > now.plusHours(2) &&
                    jobber[0].kjoeretid < now.plusHours(4),
            )
        }
    }

    @Test
    fun jobbSomErNyereEnnSlettEldreBlirIkkeSlettet() {
        bakgrunnsjobbRepository.save(bakgrunnsjobb3mndGammel)
        Assertions
            .assertThat(bakgrunnsjobb3mndGammel.uuid == (bakgrunnsjobbRepository.getById(bakgrunnsjobb3mndGammel.uuid))?.uuid)
            .isTrue()
        autoCleanJobbProcessor.prosesser(bakgrunnsjobbSlettEldreEnn10)
        Assertions
            .assertThat(bakgrunnsjobb3mndGammel.uuid == (bakgrunnsjobbRepository.getById(bakgrunnsjobb3mndGammel.uuid))?.uuid)
            .isTrue()
    }

    @Test
    fun jobbSomErEldreEnnSlettEldreBlirIkkeSlettet() {
        bakgrunnsjobbRepository.save(bakgrunnsjobb3mndGammel)
        Assertions
            .assertThat(bakgrunnsjobb3mndGammel.uuid == (bakgrunnsjobbRepository.getById(bakgrunnsjobb3mndGammel.uuid))?.uuid)
            .isTrue()
        autoCleanJobbProcessor.prosesser(bakgrunnsjobbSlettEldreEnn2)
        Assertions
            .assertThat(bakgrunnsjobb3mndGammel.uuid == (bakgrunnsjobbRepository.getById(bakgrunnsjobb3mndGammel.uuid))?.uuid)
            .isFalse()
    }

    @Test
    fun stoppeBakgrunnsserviceStopperNySkeduleringAvAutoclean() {
        bakgrunnsjobbRepository.save(bakgrunnsjobb3mndGammel)
        autoCleanJobbProcessor.prosesser(bakgrunnsjobbSlettEldreEnn2)
        Assertions.assertThat(bakgrunnsjobbRepository.findAutoCleanJobs()).hasSize(1)
        bakgrunnsjobbService.stop()
        autoCleanJobbProcessor.prosesser(bakgrunnsjobbSlettEldreEnn2)
        Assertions.assertThat(bakgrunnsjobbRepository.findAutoCleanJobs()).hasSize(1)
    }
}
