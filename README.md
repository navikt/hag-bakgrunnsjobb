HAG-bgjobb (HelseArbeidsGiver Bakgrunnsjobb)
================

Lar deg opprette jobber i database som polles og kjører i bakgrunnen.
Det er mulig å konfigurere rekjøring ved feil.

Koden er trukket ut fra hag-felles-backend-biblioteket (deprecated) og oppgradert til ktor2.
En del andre 3.parts avhengigheter er ikke oppgradert til nyeste versjon pga bakoverkompabilitet og  
for å kunne erstatte felles-versjonen med denne utgaven enklest mulig. 
Etter release av hag-bgjobb 1.0.4, vil tredjeparts biblioteker bli oppgradert 

# Komme i gang

Kjøre tester lokalt:

Starte lokal database:
cd docker/local
docker-compose up --remove-orphans

gradle build

---

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub


## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #helse-arbeidsgiver.