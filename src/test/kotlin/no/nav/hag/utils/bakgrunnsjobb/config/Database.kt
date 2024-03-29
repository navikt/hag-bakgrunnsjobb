package no.nav.hag.utils.bakgrunnsjobb.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

class Database(
    private val dbConfig: HikariConfig
) {
    val dataSource by lazy { HikariDataSource(dbConfig) }
    fun migrate() {
        migrationConfig(dbConfig)
            .let(::HikariDataSource)
            .also {
                Flyway.configure()
                    .dataSource(it)
                    .lockRetryCount(50)
                    .load()
                    .migrate()
            }.close()
    }
}

private fun migrationConfig(conf: HikariConfig): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = conf.jdbcUrl
        username = conf.username
        password = conf.password
        maximumPoolSize = 3
    }