package no.nav.hag.utils.bakgrunnsjobb

import kotlinx.serialization.json.JsonElement
import java.time.LocalDateTime
import java.util.UUID

data class Bakgrunnsjobb(
    var uuid: UUID = UUID.randomUUID(),
    var type: String,
    var behandlet: LocalDateTime? = null,
    var opprettet: LocalDateTime = LocalDateTime.now(),
    var status: BakgrunnsjobbStatus = BakgrunnsjobbStatus.OPPRETTET,
    var kjoeretid: LocalDateTime = LocalDateTime.now(),
    var forsoek: Int = 0,
    var maksAntallForsoek: Int = 3,
    var data: String="",//Dette feltet brukes ikke i exposed. Det er kun for jdbc
    var dataJson: JsonElement?=null
)

enum class BakgrunnsjobbStatus {
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
    AVBRUTT
}
