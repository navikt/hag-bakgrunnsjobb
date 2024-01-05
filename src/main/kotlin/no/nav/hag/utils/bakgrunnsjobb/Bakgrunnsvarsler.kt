package no.nav.hag.utils.bakgrunnsjobb

interface Bakgrunnsvarsler {

    fun rapporterPermanentFeiletJobb()
}

class TomVarsler() : Bakgrunnsvarsler {
    override fun rapporterPermanentFeiletJobb() {
    }
}
