package no.nav.hag.utils.bakgrunnsjobb.exposed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.jsonb

object ExposedBakgrunnsjobb : Table("bakgrunnsjobb") {
    val jobbId = javaUUID("jobb_id").uniqueIndex().autoGenerate()
    val type = varchar("type", 100)
    val behandlet = datetime("behandlet").nullable()
    val opprettet = datetime("opprettet")
    val status = enumerationByName("status", 50, Bakgrunnsjobb.Status::class)
    val kjoeretid = datetime("kjoeretid")
    val forsoek = integer("forsoek").default(0)
    val maksForsoek = integer("maks_forsoek")
    val data = jsonb<JsonElement>("data", Json).nullable()
    override val primaryKey = PrimaryKey(jobbId)
}
