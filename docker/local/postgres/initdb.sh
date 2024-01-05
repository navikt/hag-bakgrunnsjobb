#!/usr/bin/env bash
set -e

# NB:
#
# Dette er init-scriptet til docker-containeren som kjøres opp for testing
# og lokal kjøring. Ingenting av det som er her kjøres ute i miljøene (DEV/PROD)
# kun under bygg/test/ og lokal kjøring av applikasjonen.
#

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER bgjobb WITH PASSWORD 'bgjobb';
    CREATE DATABASE bgjobb_db;
    CREATE SCHEMA bgjobb;
    GRANT ALL PRIVILEGES ON DATABASE bgjobb_db TO bgjobb;
EOSQL

psql -v ON_ERROR_STOP=1 --username "bgjobb" --dbname "bgjobb_db" <<-EOSQL

EOSQL

