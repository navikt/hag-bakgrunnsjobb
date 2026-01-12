package no.nav.hag.utils.bakgrunnsjobb.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

fun createHikariConfig(container: PostgreSQLContainer): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = container.jdbcUrl
        username = container.username
        password = container.password
        maximumPoolSize = 3
        driverClassName = "org.postgresql.Driver"
    }

fun migrate(dbConfig: HikariConfig) {
    migrationConfig(dbConfig)
        .let(::HikariDataSource)
        .also {
            Flyway
                .configure()
                .dataSource(it)
                .lockRetryCount(50)
                .load()
                .migrate()
        }.close()
}

private fun migrationConfig(conf: HikariConfig): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = conf.jdbcUrl
        username = conf.username
        password = conf.password
        maximumPoolSize = 3
    }
