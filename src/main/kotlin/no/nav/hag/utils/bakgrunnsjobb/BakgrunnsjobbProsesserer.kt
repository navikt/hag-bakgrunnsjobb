package no.nav.hag.utils.bakgrunnsjobb

import java.time.LocalDateTime

/**
 * Interface for en klasse som kan prosessere en bakgrunnsjobbstype
 */
interface BakgrunnsjobbProsesserer {
    val type: String

    /**
     * Logikken som skal håndtere jobben. Får inn en kopi av jobben med all metadata
     */
    fun prosesser(jobb: Bakgrunnsjobb)

    /**
     * Logikk som skal kjøres når jobben stoppes helt opp fordi maks antall forsøk er nådd.
     * Får inn en kopi av jobben med all metadata
     */
    fun stoppet(jobb: Bakgrunnsjobb) {}

    /**
     * Default backoffløsning
     * Antall forsøk bestemmer hvor mange ganger en jobb blir forsøkt på nytt.
     * Denne metoden bestemmer hvor lang tid det tar i kalendertid før en jobb stoppes pga max forsøk.
     * Tabellen under kan brukes for å velge hvor lenge man lenge, dvs hvor mange forsøk, som forsøkes før jobbens stoppes.
     *
     * For eksempel hvis default backoffløsning velges med 13 antall forsøk vil jobben stoppes etter 13 forsøk
     * fordelt over 7 dager.
     *
     * ```
     * | Forsøk | Tid mellom | Påløpte | Påløpte |
     * | nummer | forsøkene  | timer   | dager   |
     * |--------|------------|---------|---------|
     * |    1   |      1     |     1   |   0,0   |
     * |    2   |      3     |     4   |   0,2   |
     * |    3   |      5     |     9   |   0,4   |
     * |    4   |      7     |    16   |   0,7   |
     * |    5   |      9     |    25   |   1,0   |
     * |    6   |     11     |    36   |   1,5   |
     * |    7   |     13     |    49   |   2,0   |
     * |    8   |     15     |    64   |   2,7   |
     * |    9   |     17     |    81   |   3,4   |
     * |   10   |     19     |   100   |   4,2   |
     * |   11   |     21     |   121   |   5,0   |
     * |   12   |     23     |   144   |   6,0   |
     * |   13   |     25     |   169   |   7,0   |
     * |   14   |     27     |   196   |   8,2   |
     * |   15   |     29     |   225   |   9,4   |
     * |   16   |     31     |   256   |  10,7   |
     * |--------|------------|---------|---------|
     * ```
     */
    fun nesteForsoek(
        forsoek: Int,
        forrigeForsoek: LocalDateTime,
    ): LocalDateTime {
        val backoffWaitInHours = (forsoek * 2) - 1
        return LocalDateTime.now().plusHours(backoffWaitInHours.toLong())
    }
}
