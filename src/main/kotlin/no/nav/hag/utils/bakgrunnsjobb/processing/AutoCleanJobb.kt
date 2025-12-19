package no.nav.hag.utils.bakgrunnsjobb.processing

import java.time.LocalDateTime
import java.util.UUID

data class AutoCleanJobb(
    val id: UUID = UUID.randomUUID(),
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val slettEldre: Long,
    val interval: Int,
)
