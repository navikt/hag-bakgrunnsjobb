package no.nav.hag.utils.bakgrunnsjobb

import kotlinx.serialization.json.JsonElement
import java.time.LocalDateTime
import java.util.UUID

data class Bakgrunnsjobb(
    val uuid: UUID = UUID.randomUUID(),
    val type: String,
    val behandlet: LocalDateTime? = null,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val status: Status = Status.OPPRETTET,
    val kjoeretid: LocalDateTime = LocalDateTime.now(),
    val forsoek: Int = 0,
    val maksAntallForsoek: Int = 3,
    val data: String = "", // Dette feltet brukes ikke i exposed. Det er kun for jdbc
    val dataJson: JsonElement? = null,
) {
    enum class Status {
        /**
         * Oppgaven er opprettet og venter på kjøring
         */
        OPPRETTET,

        /**
         * Oppgaven har blitt forsøkt kjørt, men feilet. Den vil bli kjørt igjen til den når maks antall forsøk
         */
        FEILET,

        /**
         * Oppgaven ble kjørt maks antall forsøk og trenger nå manuell håndtering
         */
        STOPPET,

        /**
         * Oppgaven ble kjørt OK
         */
        OK,

        /**
         * Oppgaven er manuelt avbrutt
         */
        AVBRUTT,
    }
}
