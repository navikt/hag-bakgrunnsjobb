package no.nav.hag.utils.bakgrunnsjobb.config

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class WithPostgresContainer {
    val postgresContainer =
        PostgreSQLContainer("postgres:latest").apply {
            setCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all", "-c", "wal_level=logical")

            withReuse(true)
            withLabel("app-navn", "test-database")
            withUsername("bgjobb-username")
            withPassword("bgjobb-password")

            println("Starter Postgres-container...")
            start()
            println(
                "Postgres-container er klar, med portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: $username og $password",
            )
        }

    @AfterAll
    fun stopContainer() {
        println("Stopper Postgres-container...")
        postgresContainer.stop()
        println("Postgres-container er stoppet.")
    }
}
