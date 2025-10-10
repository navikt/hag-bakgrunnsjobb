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

Legg til denne i flyway db.migration scripts for å opprette nødvendig tabell:

```SQL
create table bakgrunnsjobb
(
jobb_id      uuid unique  not null primary key,
type         VARCHAR(100) not null,
behandlet    timestamp,
opprettet    timestamp    not null,

    status       VARCHAR(50)  not null,
    kjoeretid    timestamp    not null,

    forsoek      int          not null default 0,
    maks_forsoek int          not null,
    data         jsonb
);
CREATE INDEX idx_bgjobb_kjoeretid_status ON BAKGRUNNSJOBB(KJOERETID, STATUS);
```

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
