package no.nav.hag.utils.bakgrunnsjobb

import no.nav.hag.utils.bakgrunnsjobb.processing.AutoCleanJobbProcessor
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

internal object Jackson {
    private val om: ObjectMapper = jacksonObjectMapper()

    fun toJson(data: AutoCleanJobbProcessor.JobbData): String = om.writeValueAsString(data)

    fun fromJson(data: String): AutoCleanJobbProcessor.JobbData = om.readValue(data)
}
