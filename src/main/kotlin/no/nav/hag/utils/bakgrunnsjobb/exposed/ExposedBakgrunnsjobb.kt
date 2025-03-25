package no.nav.hag.utils.bakgrunnsjobb.exposed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb


object ExposedBakgrunnsjobb : Table("bakgrunnsjobb") {
    val jobbId = uuid("jobb_id").uniqueIndex().autoGenerate()
    val type = varchar("type", 100)
    val behandlet = datetime("behandlet").nullable()
    val opprettet = datetime("opprettet")
    val status = enumerationByName("status", 50, BakgrunnsjobbStatus::class)
    val kjoeretid = datetime("kjoeretid")
    val forsoek = integer("forsoek").default(0)
    val maksForsoek = integer("maks_forsoek")
    val data = jsonb<JsonElement>("data", Json)
    override val primaryKey = PrimaryKey(jobbId)
}