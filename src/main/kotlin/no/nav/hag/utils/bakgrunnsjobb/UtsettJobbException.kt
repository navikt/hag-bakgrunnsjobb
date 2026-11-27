package no.nav.hag.utils.bakgrunnsjobb

import kotlin.time.Duration

/**
 * Kastes fra [BakgrunnsjobbProsesserer.prosesser] for å utsette en jobb uten at det regnes som et feilet forsøk.
 * Jobben beholder status og antall forsøk, og kjøres igjen tidligst etter [utsettelse].
 */
class UtsettJobbException(
    val utsettelse: Duration,
    message: String,
) : RuntimeException(message) {
    init {
        // En utsettelse på 0 ville gjort at jobben plukkes opp igjen umiddelbart i samme kjøring.
        require(utsettelse.isPositive()) { "Utsettelse må være positiv, men var $utsettelse." }
    }
}
