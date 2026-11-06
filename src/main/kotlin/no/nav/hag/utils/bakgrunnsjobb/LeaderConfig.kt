package no.nav.hag.utils.bakgrunnsjobb

/**
 * Avgjør om podden er valgt leder. BakgrunnsjobbService kjører kun jobber på leder-podden.
 * Ved flere podder kan appen implementere denne med Nais sin leader election
 * (https://doc.nais.io/services/leader-election/).
 */
fun interface LeaderConfig {
    fun isElectedLeader(): Boolean
}

/** Standardoppførsel: alle podder kjører bakgrunnsjobber. */
object AlltidLeader : LeaderConfig {
    override fun isElectedLeader(): Boolean = true
}
