package no.nav.hag.utils.bakgrunnsjobb

import no.nav.hag.utils.bakgrunnsjobb.config.WithPostgresContainer
import no.nav.hag.utils.bakgrunnsjobb.config.createHikariConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseUtilsKtTest : WithPostgresContainer() {
    @Test
    fun createTestHikariConfigWithCorrectParametersOKTest() {
        val config = createHikariConfig(postgresContainer)
        Assertions.assertEquals("bgjobb-username", config.username)
        Assertions.assertEquals("bgjobb-password", config.password)
        Assertions.assertEquals(3, config.maximumPoolSize)
        Assertions.assertEquals("org.postgresql.Driver", config.driverClassName)
    }
}
