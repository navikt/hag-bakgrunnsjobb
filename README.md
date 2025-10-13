HAG-bgjobb (HelseArbeidsGiver Bakgrunnsjobb)
================

Lar deg opprette jobber i database som polles og kjører i bakgrunnen.
Det er mulig å konfigurere rekjøring ved feil.

Koden er trukket ut fra hag-felles-backend-biblioteket (deprecated) og oppgradert til ktor2.

# Komme i gang

Kjøre tester lokalt:

Starte lokal database:
cd docker/local
docker-compose up --remove-orphans

gradle build

For å ta i bruk: Importer biblioteket (gradle):
````
implementation("no.nav.helsearbeidsgiver:hag-bakgrunnsjobb:$bakgrunnsjobbVersion")
````

Kopier flyway sql-scripts fra `src.test.resources.db.migrations` for å opprette databasetabeller

Lag en eller flere jobber som du ønsker å kjøre - disse må implementere BakgrunnsjobbProsesserer

Ved oppstart av applikasjon: 

Instansier en BakgrunnsjobbService, registrer prosessor(er) og start:

```
val bgService = BakgrunnsjobbService()
bgService.registrer(MinProcessor())
bgService.startAsync(retryOnFail = true)
```

---

# Releasing
Bump version i build.gradle.kts og push til main
Gjør deretter release fra github-gui, versjon settes lik som versjon i build-fila.

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub


## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #helse-arbeidsgiver.
