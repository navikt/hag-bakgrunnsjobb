package no.nav.hag.utils.bakgrunnsjobb.exposed


import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.hag.utils.bakgrunnsjobb.TransactionalExtension
import no.nav.hag.utils.bakgrunnsjobb.config.createLocalHikariConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(TransactionalExtension::class)
class ExposedBakgrunnsjobRepositoryTest {

    private lateinit var repository: ExposedBakgrunnsjobRepository

    val dataSource = HikariDataSource(createLocalHikariConfig())

    val database = Database.connect(dataSource)


    @BeforeEach
    fun setup() {
        repository = ExposedBakgrunnsjobRepository(database)
        no.nav.hag.utils.bakgrunnsjobb.config.Database(createLocalHikariConfig()).migrate()
    }


    @Test
    fun `Lagrer en jobb med save og returnerer raden med getById`() {
        transaction {

            val testUuid = UUID.randomUUID()
            val testJson = buildJsonObject { put("key", "value") }
            val testOpprettet = LocalDateTime.now().truncatedTo(ChronoUnit.NANOS)
            val testKjoeretid = testOpprettet.plusHours(1)

            repository.save(
                Bakgrunnsjobb(
                    uuid = testUuid,
                    type = "testType",
                    opprettet = testOpprettet,
                    behandlet = null,
                    status = BakgrunnsjobbStatus.OPPRETTET,
                    kjoeretid = testKjoeretid,
                    forsoek = 1,
                    maksAntallForsoek = 3,
                    data = Json.encodeToString(testJson)
                )
            )


            val bakgrunnsjobb = repository.getById(testUuid)


            assertNotNull(bakgrunnsjobb)
            assertEquals(testUuid, bakgrunnsjobb!!.uuid)
            assertEquals("testType", bakgrunnsjobb.type)
            assertNull(bakgrunnsjobb.behandlet)
            assertNotNull(bakgrunnsjobb.opprettet)
            assertNotNull(bakgrunnsjobb.kjoeretid)
            assertEquals(1, bakgrunnsjobb.forsoek)
            assertEquals(3, bakgrunnsjobb.maksAntallForsoek)
            assertEquals(Json.encodeToString(testJson), bakgrunnsjobb.data)
        }
    }

    @Test
    fun `getById returer null hvis jobben ikke finnes`() {
        transaction {
            // Given: A random UUID that doesn't exist
            val nonExistentUuid = UUID.randomUUID()

            // When: Trying to fetch a non-existent job
            val bakgrunnsjobb = repository.getById(nonExistentUuid)

            // Then: It should return null
            assertNull(bakgrunnsjobb)
        }
    }
}
