package no.nav.hag.utils.bakgrunnsjobb.processing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.test.TestScope
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.hag.utils.bakgrunnsjobb.MockBakgrunnsjobbRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class AutoCleanJobbProcessorTest {
    val now = LocalDateTime.now()
    lateinit var autoCleanJobbProcessor: AutoCleanJobbProcessor
    lateinit var bakgrunnsjobbRepository: BakgrunnsjobbRepository
    lateinit var bakgrunnsjobbService: BakgrunnsjobbService
    val bakgrunnsjobbSlettEldreEnn10 =
        Bakgrunnsjobb(
            UUID.randomUUID(),
            AutoCleanJobbProcessor.JOB_TYPE,
            now,
            now,
            BakgrunnsjobbStatus.OPPRETTET,
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
            BakgrunnsjobbStatus.OPPRETTET,
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
            BakgrunnsjobbStatus.OK,
            now.minusMonths(3),
            0,
            3,
            "{}",
        )

    @BeforeEach
    fun setUp() {
        bakgrunnsjobbRepository = MockBakgrunnsjobbRepository()
        val testScope = TestScope()
        bakgrunnsjobbService = BakgrunnsjobbService(bakgrunnsjobbRepository, 1, testScope)
        val objectMapper =
            ObjectMapper().apply {
                registerKotlinModule()
            }
        autoCleanJobbProcessor = AutoCleanJobbProcessor(bakgrunnsjobbRepository, bakgrunnsjobbService, objectMapper)
    }

    @Test
    fun getType() {
        Assertions.assertThat(AutoCleanJobbProcessor.JOB_TYPE == autoCleanJobbProcessor.type).isTrue()
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
        Assertions.assertThat(bakgrunnsjobbRepository.findAutoCleanJobs().size == 1)
        bakgrunnsjobbService.stop()
        autoCleanJobbProcessor.prosesser(bakgrunnsjobbSlettEldreEnn2)
        Assertions.assertThat(bakgrunnsjobbRepository.findAutoCleanJobs().size == 0)
    }
}
