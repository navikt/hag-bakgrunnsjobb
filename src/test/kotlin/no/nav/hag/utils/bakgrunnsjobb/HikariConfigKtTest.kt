package no.nav.hag.utils.bakgrunnsjobb

import no.nav.hag.utils.bakgrunnsjobb.config.createHikariConfig
import no.nav.hag.utils.bakgrunnsjobb.config.createLocalHikariConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class HikariConfigKtTest {

    @Test
    fun testCreateHikariConfig() {
        val hikariConfig = createHikariConfig("jdbc:postgresql://localhost:5432/bgjobb_db", "harbeidsgiverbackend", "harbeidsgiverbacken")
        Assertions.assertEquals("jdbc:postgresql://localhost:5432/bgjobb_db", hikariConfig.jdbcUrl)
    }

    @Test
    fun createTestHikariConfigWithCorrectParametersOKTest() {
        val localHikariConfig = createLocalHikariConfig()
        Assertions.assertEquals("org.postgresql.Driver", localHikariConfig.driverClassName)
        Assertions.assertEquals("jdbc:postgresql://localhost:5432/bgjobb_db", localHikariConfig.jdbcUrl)
        Assertions.assertEquals("bgjobb", localHikariConfig.username)
        Assertions.assertEquals("bgjobb", localHikariConfig.password)
    }
    @Test
    fun createTestHikariConfigWithIncorrectUserKOTest() {
        val localHiariConfig = createLocalHikariConfig()
        Assertions.assertEquals("org.postgresql.Driver", localHiariConfig.driverClassName)
        Assertions.assertEquals("jdbc:postgresql://localhost:5432/bgjobb_db", localHiariConfig.jdbcUrl)
        Assertions.assertNotEquals("feilbruker", localHiariConfig.username)
        Assertions.assertEquals("bgjobb", localHiariConfig.password)
    }
}
